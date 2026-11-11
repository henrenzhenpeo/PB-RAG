# ADR 0008：关键词召回采用 ParadeDB BM25

- 状态：已接受
- 日期：2026-11-11
- 影响模块：`retrieval`、`evaluation`、PostgreSQL 迁移
- 替代：ADR 0007 中的 `pg_trgm` 关键词召回基线

## 问题

`pg_trgm` 通过字符片段相似度补充向量召回，但它不是词项相关性排序，无法提供标准 BM25 的词频、逆文档频率和文档长度归一化。当前语料以中文技术文档、接口名、类名和错误码为主，需要建立可评测的中文 BM25 基线，同时保持现有资源过滤、RRF、Rerank 和引用链路不变。

## PB-RAG 方案

在 PostgreSQL 18 上使用已安装的 ParadeDB `pg_search` 扩展，为 `chunks` 创建 BM25 索引：

- `id` 作为稳定 `key_field`；
- `content` 使用 `chinese_lindera` tokenizer；
- `workspace_id` 和 `document_version_id` 一并进入索引，为资源条件和后续查询优化保留空间；
- 查询使用 `paradedb.match`，按 `paradedb.score` 降序取 Top 30；
- Workspace、知识库和 `documents.current_version_id` 条件仍在召回 SQL 中执行，禁止跨范围召回后再由应用过滤。

流水线保持：

```text
Vector Top 30 + BM25 Top 30
  → 按 chunk_id 去重
  → RRF(k=60) Top 30
  → 可选 Rerank Top 10
  → Context Top 6
```

BM25 分数只作为 `KEYWORD` 阶段观测数据保存。RRF 只使用两路排名，不归一化或直接相加向量分数与 BM25 分数。动态权重暂不加入，必须等固定数据集证明收益并具备意图分类降级策略后再决策。

数据库迁移 V8 创建 `pg_search` 扩展、删除 trigram 索引并创建 BM25 索引。为保证已部署环境的 Flyway 历史不可变，V7 保留原始 `pg_trgm` 建表记录；V8 不删除 `pg_trgm` 扩展，避免影响数据库内潜在的其他使用者。

## 与 WeKnora 的机制对照

WeKnora 的 ParadeDB 方案同样使用中文 tokenizer、BM25 召回和后续融合。PB-RAG 保留自己的领域查询和运行追踪：资源作用域由 SQL 确定性约束，融合由项目内 RRF 实现，每个阶段与固定评测样本关联，不复制其数据模型或检索编排。

## 负面影响

- 运行环境必须预装与 PostgreSQL 18 匹配的 `pg_search`，普通 PostgreSQL contrib 不包含它；
- BM25 索引会增加存储和 Chunk 写入成本；
- `chinese_lindera` 的分词语义及中英文技术标识效果必须通过 `retrieval-v1` 评测，不能仅凭实现完成宣称质量提升；
- ParadeDB 版本升级可能改变 SQL 或索引能力，升级前必须重新执行迁移与查询集成验证。

## 验证

- 在 PostgreSQL 18.6、`pg_search` 0.26.6 上验证迁移、中文查询、BM25 分数排序和 Top 30；
- 验证错误 Workspace、知识库和非当前文档版本不能进入候选；
- 在 `retrieval-v1` 上分别记录 Vector、BM25、RRF 和 Rerank 的 Recall@1/3/6、MRR、拒答率与延迟；
- 保存数据库版本、`pg_search` 版本、tokenizer、候选数量和代码版本，确保结果可复现。

