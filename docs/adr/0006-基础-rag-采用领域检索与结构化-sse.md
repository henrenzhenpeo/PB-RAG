# ADR 0006：基础 RAG 采用领域检索与结构化 SSE

- 状态：已接受
- 日期：2026-11-10
- 影响模块：`retrieval`、`chat`、`prompts/rag`、前端知识问答页

## 问题

入库已经可以生成带来源位置的 Chunk 和向量。第一周闭环需要回答两个问题：检索是否直接使用 Spring AI 默认 `PgVectorStore`，以及模型流和引用如何传给前端。

## 决定

1. Spring AI `EmbeddingModel` 和 `StreamingChatModel` 只负责模型协议；检索继续使用 PB-RAG 的 `chunks`、文档版本和 Workspace 领域表。
2. Top-K SQL 必须同时约束 `workspace_id`、知识库和文档当前版本，只返回向量非空的 Chunk。
3. 引用绑定 `chunk_id`、`document_id`、`document_version_id` 和 `source_position`，不能只保存模型生成的脚注文本。
4. 没有达到相似度阈值的证据时由确定性代码拒答，不调用 Chat 模型。
5. 回答通过具名 SSE 事件输出：`status`、`citations`、`delta`、`completed`、`error`。`status` 即时反馈检索与生成阶段，引用先于回答文本发送，前端不从自然语言中反向猜测引用。
6. 运行时 Prompt 保存在 `prompts/` 并随构建复制到 classpath；知识库内容始终按不可信数据处理。
7. 会话绑定当前登录用户、Workspace 和知识库；每轮问题与助手消息共享 `turn_id`，引用持久化到助手消息并保存受限原文摘录。
8. 助手消息显式记录 `STREAMING`、`COMPLETED` 或 `INTERRUPTED`，客户端停止、模型断流与服务重启不会留下含义不明的半成品。
9. 用户停止不是单纯关闭浏览器连接：显式停止 API 必须验证用户及完整资源作用域，原子保存 `INTERRUPTED/CLIENT_CANCELLED` 和部分正文，再通过进程内注册表取消对应模型流；连接断开只作为兜底路径。

## 与 WeKnora 的机制对照

WeKnora 同样把知识资源过滤、检索和引用作为模型之外的确定性链路。PB-RAG 不复制其目录或实现，而是保留相同工程原则：模型不能决定资源边界，引用必须来自真实检索证据，中间结果需要可观察并可进入后续评测。

## 当前边界

- 本 ADR 只完成基础向量检索，不包含 FTS、RRF、Rerank 和邻接扩展；
- 当前持久化会话、消息和引用，但尚未将历史轮次加入模型上下文；多轮上下文策略在获得评测基线后单独设计，避免无界拼接；
- 数据库并发与迁移集成测试按当前优先级后移，但不能在进入可靠性验收前永久省略。

## 后续验证

- 使用真实文档验证 Top-K、无证据拒答、SSE 中断和引用位置；
- 建立固定问题集，记录 Recall@K、引用准确率、延迟和模型配置；
- 第二周在相同接口后加入 FTS、RRF 和独立 Rerank 适配器。
