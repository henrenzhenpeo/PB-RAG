# PB-RAG 当前交接

> 本文件只记录当前事实、验证、风险和下一步。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一周纵向 RAG 闭环和第二周 Production RAG 主链路已完成。系统已具备真实文档入库、百炼 Embedding、pgvector 与 ParadeDB BM25 双路召回、RRF、可选 Rerank、检索阶段记录、千问流式回答、版本化引用、会话持久化、刷新恢复和显式停止能力。

当前重点是完成 `retrieval-v1` 真值复核和统一基线评测，并补齐 PostgreSQL 自动化集成验证。完成后进入第三周 Agent Runtime。

## 已完成能力

### 工程与资源模型

- Spring Boot 4.1.2 / Java 21 后端与 Vue 3 / TypeScript 前端骨架；
- PostgreSQL、pgvector、ParadeDB、Redis、Flyway 和 MyBatis-Plus 分层；
- Workspace、知识库、文档、版本、Chunk、入库 Job、会话、消息和引用模型；
- Workspace 与知识库创建、列表、详情，文档上传、列表和 Job 查询；
- 邮箱注册、登录、退出和个人资料；所有已登录用户仍共享 Workspace；
- `X-Request-Id`、Trace/Span、Workspace MDC、结构化访问日志和滚动文件日志；
- ArchUnit 约束 Controller/Service/Mapper 与 JDBC 边界。

### 文档入库

- Markdown 与普通文本 PDF 解析；
- 流式 SHA-256、本地对象存储、事务创建和失败补偿；
- 同知识库相同内容幂等返回已有版本与 Job；
- 标题感知分块及字符偏移、Markdown 行号、标题路径和 PDF 页码；
- PostgreSQL `FOR UPDATE SKIP LOCKED` 领取、租约、过期恢复、有限重试和结构化失败；
- 1024 维 Embedding 校验、批量写入和 HNSW cosine 索引；
- 状态迁移在领域层和数据库条件更新两层约束。

### Production RAG 与问答

- pgvector 与 ParadeDB BM25（`chinese_lindera`）各召回 Top 30；
- RRF(k=60)、独立 `RerankClient`、瞬时故障降级和最终 Top 6 上下文；
- `retrieval_runs` / `retrieval_candidates` 保存 VECTOR、KEYWORD、FUSION、RERANK、CONTEXT 阶段；
- V9 增加 `retrieval_stage_summaries`，保存零结果、跳过和降级阶段的状态、候选数、耗时与错误码；运行记录保存实际最终阶段和降级原因；
- `/retrieval/search` 与 `/retrieval/debug` 支持检索及阶段快照查看；
- 只检索当前文档版本，并返回 Chunk、版本、稳定来源位置和分数；
- BM25 使用可配置最低证据分数，当前基线为 `1.0`；最终 `CONTEXT` 同时执行 Top-K 与字符预算裁剪，因此阶段记录等于真实 Prompt 证据；
- 百炼 Rerank 配置独立连接/读取超时，校验结果数量、下标、重复项和非有限分数，瞬时失败显式降级到 FUSION；
- 确定性无证据拒答、版本化 Prompt、结构化 SSE 和受限引用原文摘录；
- 会话、消息和引用按当前用户持久化，刷新后可恢复；
- 助手消息有 `STREAMING`、`COMPLETED`、`INTERRUPTED` 明确终态；
- 显式停止 API 在完整用户与资源作用域内持久化部分正文并取消本实例模型流；连接断开作为兜底。
- Prompt 元数据解析兼容 LF、Windows CRLF 和可选 UTF-8 BOM，避免不同 Git 换行配置导致应用启动失败。

### 评测资产

- `evaluation/corpus/retrieval-v1-manifest.json` 冻结 4 个当前文档版本、377 个已完成 Embedding 的 Chunk；
- `evaluation/datasets/retrieval-v1.jsonl` 包含 40 条人工真值草案，其中 32 条可回答、8 条拒答；
- 证据绑定文档版本、Chunk 和稳定来源位置；尚未完成人工复核和正式基线运行。

## 当前关键配置与决定

- Chat/RAG：`qwen3.7-flash`；Embedding：`qwen3.7-text-embedding-flash`，1024 维；
- Rerank：`qwen3.7-text-rerank`，默认关闭，需配置业务空间专属 URL 与 API Key 后实测；
- PostgreSQL Job 表是任务事实来源，Redis 只负责唤醒、取消标记和限流；
- Flyway 是唯一 DDL 管理方式，业务访问统一经过 MyBatis-Plus Mapper；
- Workspace 是共享知识资源分组，不是授权边界；
- Docker 一键启动和 CI 暂低于 Production RAG、Agent Runtime 与评测闭环优先级。

## 验证状态

- 后端 JDK 21 全量测试最近记录为 44 个测试：43 个通过，1 个 Spring 上下文测试因未接入 Testcontainers 跳过；
- Production RAG 单元测试覆盖 RRF 去重、Rerank 排序、瞬时故障降级和向量维度失败终态；
- ParadeDB 开发库为 PostgreSQL 18.6、`pg_search` 0.26.6；V8 已应用，BM25 查询使用 `Custom Scan (ParadeDB Base Scan)`，错误 Workspace 返回 0；
- 真实 PostgreSQL 已成功应用到 V8，并完成 Spring 上下文启动验证；
- 前端最近已通过 `type-check`、`lint` 和生产构建；
- Mapper XML 语法检查和 `git diff --check` 最近均通过；
- 显式停止闭环的新增测试代码尚未执行完整编译与测试，不能视为已验证完成。
- Prompt 跨平台换行修复已补充 CRLF + BOM 回归测试代码，按协作规则尚未执行编译与测试。
- 检索结果可追溯增强、BM25 证据门槛、真实 Context 裁剪和 Rerank 超时的代码及回归测试已补充；按协作规则尚未执行编译、V9 迁移或真实 Rerank 调用。

以上是最近一次有效验证快照。后续运行应以新结果覆盖本节，不追加历史流水。

## 当前风险

- `retrieval-v1` 的 40 条真值仍是草案；在人工复核和正式运行前，不能宣称 BM25、RRF 或 Rerank 带来质量提升；
- BM25 最低分数 `1.0` 只是首版保守基线，必须通过可回答与拒答样本共同校准，避免过度召回或误拒答；
- `qwen3.7-text-rerank` 尚未启用和实调，供应商地址、限流、延迟、成本和降级行为仍需记录；
- 完整 Spring 上下文、Flyway、Mapper SQL、`SKIP LOCKED` 和租约恢复尚未由 Testcontainers 自动验证；
- 停止、模型流中断、断连和页面刷新恢复需要在真实服务与真实文档上继续回测；
- 所有已登录用户共享资源；开放注册只适合受控开发环境，公网部署前需要注册限制、登录限流、密码重置、会话清理和正式授权模型；
- 当前模型别名可能自动升级，评测必须记录可获得的具体模型版本和运行配置。

## 下一步

1. 人工复核 `retrieval-v1` 的 40 条问题、期望证据和拒答样本；
2. 在同一语料与配置上运行 Vector、BM25、RRF，记录 Recall@1/3/6、MRR、拒答率和延迟，并校准 BM25 最低证据分数；
3. 配置并实调 `qwen3.7-text-rerank`，比较 RRF 与 Rerank Top 10 / Context Top 6；
4. 用真实文档回测 SSE、无证据拒答、版本引用、显式停止、断流和刷新恢复；
5. 补充 PostgreSQL 自动化集成测试，覆盖 V8/V9、阶段摘要、最终阶段、资源作用域、当前版本过滤、`SKIP LOCKED` 和租约恢复；
6. 固化评测报告和失败样本后，进入 Agent Run/Step 状态机与最小 ReAct 循环。
