# ADR 0007：Production RAG 采用混合检索与可追溯阶段结果

- 状态：已接受
- 日期：2026-11-11
- 影响模块：`retrieval`、`chat`、`evaluation`、PostgreSQL 迁移

> 关键词召回的 `pg_trgm` 基线已由 ADR 0008 的 ParadeDB BM25 方案替代；RRF、Rerank、资源作用域和阶段追踪决策继续有效。

## 问题

第一周的单路 pgvector Top-K 可以完成最短 RAG 闭环，但无法稳定覆盖接口名、错误码、类名等精确词，也无法解释候选在向量、关键词、融合和重排阶段为何进入或退出最终上下文。第二周需要建立可评测、可回放且保持 Workspace 和文档版本作用域的 Production RAG。

## PB-RAG 方案

首版流水线固定为：

```text
问题规范化与资源校验
  → Query Embedding
  ├─ Vector Top 30
  └─ Keyword Top 30
  → 按 chunk_id 去重
  → RRF(k=60) Top 30
  → qwen3.7-text-rerank Top 10
  → 上下文预算裁剪 Top 6
  → 引用与回答
```

Query Rewrite、邻接 Chunk 扩展和历史问题拼接暂不进入首版，避免同时改变多个变量而无法归因。

### 资源和版本边界

- Vector 与 Keyword SQL 都必须同时约束 `workspace_id`、`knowledge_base_id` 和 `documents.current_version_id`；
- 资源过滤发生在召回 SQL 中，不允许先跨范围召回再在内存过滤；
- 引用继续绑定 `chunk_id`、`document_id`、`document_version_id` 和稳定来源位置；
- 文档内容属于不可信数据，不能改变检索策略、资源范围或工具策略。

### 关键词召回

当前固定语料以中文、接口路径、类名和错误码为主。PostgreSQL 默认 `tsvector` 配置不能提供可信的中文分词基线，因此首版启用 PostgreSQL 官方 contrib 扩展 `pg_trgm`，使用 `word_similarity(query, content)` 进行关键词/字符片段召回，并建立 `gin_trgm_ops` 索引。

这仍是 PostgreSQL 内部的确定性关键词召回，不引入 Elasticsearch 或新服务。后续必须在 `retrieval-v1` 上比较 `pg_trgm`、中文分词 FTS 或组合策略；没有评测收益时不增加扩展。

### RRF

融合采用 Reciprocal Rank Fusion：

```text
score(chunk) = Σ 1 / (60 + rank_in_source)
```

同一 Chunk 在两路出现时累加。RRF 只融合排名，不直接混合 cosine similarity 与 trigram score，因为二者不在同一量纲。相同 RRF 分数依次按最佳来源排名和 Chunk ID 保证确定性排序。

### Rerank

Rerank 通过项目内 `RerankClient` 接口接入，Production RAG 不依赖供应商请求结构。当前适配器使用百炼 `qwen3.7-text-rerank`，请求只发送 RRF 候选正文，并校验返回下标、重复下标和分数。

- 超时、限流和供应商 5xx：记录降级原因并使用 RRF 顺序；
- 认证、配置、响应协议或下标错误：检索失败，不静默伪装成有效重排；
- 模型未启用：明确记录 `RERANK_SKIPPED`，使用 RRF 顺序；
- Rerank 分数仅在同一次请求内比较，不跨运行比较。

### 中间结果

使用两张表：

- `retrieval_runs`：一次检索的资源范围、问题、策略、配置、模型、状态、耗时和错误；
- `retrieval_candidates`：`VECTOR`、`KEYWORD`、`FUSION`、`RERANK`、`CONTEXT` 各阶段的 Chunk、文档版本、排名、分数和 JSONB 元数据。

阶段结果不重复保存 Chunk 正文，正文继续以版本化 Chunk 为事实来源。候选表故意不对 Chunk 建删除约束：入库重试会按版本原子替换 Chunk，历史检索记录必须保留其原始标识而不能阻塞恢复。普通在线运行的保留周期后续由运维策略处理；评测运行长期保留。

### 失败语义

检索运行先写入 `RUNNING`，成功后进入 `COMPLETED`；模型、SQL、持久化或协议异常进入 `FAILED` 并记录稳定错误码。阶段持久化失败会让本次检索失败，避免返回一个无法审计却被声称可追溯的结果。

## 与 WeKnora 的机制对照

WeKnora 同样将多路召回、融合、重排和知识资源过滤放在模型回答之前。PB-RAG 不复制其目录或实现，而是保留可迁移机制：确定性资源过滤、多阶段候选、独立重排边界和可观察中间结果。PB-RAG 额外把每次阶段结果与固定评测样本关联作为首期核心，便于用相同语料比较 Vector、Hybrid、RRF 和 Rerank。

## 负面影响

- 每次检索增加一次关键词 SQL、阶段写入和可选的远程 Rerank 调用；
- `pg_trgm` 是字符片段基线，不理解中文词义，必须通过评测决定是否升级；
- 中间结果表会增长，需要后续增加保留和归档策略；
- Rerank 供应商故障可能造成质量降级，因此运行结果必须显示实际采用的最终阶段。

## 验证

- 在 `evaluation/datasets/retrieval-v1.jsonl` 上记录 Vector、Keyword、RRF 和 Rerank 的 Recall@1/3/6、MRR、拒答率和延迟；
- 验证错误 Workspace/知识库不能产生候选；
- 验证相同输入排名确定、RRF 去重正确、Rerank 下标校验和降级语义；
- 使用真实 PostgreSQL 验证迁移、trigram 查询、阶段写入和当前版本切换；
- 只有固定数据集上的可重复结果才能支持“Hybrid/Rerank 有提升”的结论。
