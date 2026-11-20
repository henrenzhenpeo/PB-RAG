# PB-RAG 当前交接

> 本文件只记录当前事实、最近有效验证、开放风险和下一步。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`，完整评测数据见 `../evaluation/reports/`。

## 当前阶段

第一周 RAG 纵向闭环和第二周 Production RAG 已完成，`retrieval-v1` 的 40 条正式基线与 Fusion Top 30/60 对照也已完成。Top 60 在相同数据集上将 Context Evidence Recall@6 从 94.12% 提升到 100%，8 条不可回答样本保持正确拒答，满足进入第三周 Agent Runtime 的质量准入条件。

第三周首个同步 Agent 切片已实现并完成一次本地真实模型调用：Run/Step/Tool Execution 事实表、状态迁移、显式 Tool Registry、`calculator` 与模型原生 Tool Call 循环。单元测试、取消、故障与恢复仍待验证。LangGraph4j 只在生产 Runtime 与工具契约稳定后做同条件对照。

## 当前能力

### 工程、资源与入库

- Spring Boot 4.1.2 / Java 21 后端和 Vue 3 / TypeScript 前端采用包级模块化单体；
- PostgreSQL、pgvector、ParadeDB、Redis、Flyway 与 MyBatis-Plus 已接入，Flyway 是唯一 DDL 管理方式；
- Workspace、知识库、文档、不可变版本、Chunk、入库 Job、会话、消息与版本化引用已形成完整资源链；
- Markdown 与普通文本 PDF 支持解析、标题感知分块、1024 维 Embedding、HNSW 索引和稳定来源位置；
- PostgreSQL Job 表负责状态事实，支持 `SKIP LOCKED` 领取、租约、过期恢复、有限重试、取消和完成时的当前版本原子切换；
- Redis 仅负责 Pub/Sub 唤醒和 TTL 取消加速，连接失败时由 PostgreSQL 轮询兜底；
- 文档重复上传幂等，已有文档可创建递增版本，失败或处理中不会替换当前生产版本；
- 邮箱注册、登录、退出和个人资料已实现；当前所有登录用户共享 Workspace，尚无 RBAC；
- Request/Trace/Span/Workspace 关联、结构化日志、滚动日志和 ArchUnit 分层约束已建立；
- 前端支持知识库、文档上传/版本、入库状态/取消、检索调试、流式问答、引用和会话恢复。

### Production RAG 与问答

- pgvector Vector 与 ParadeDB BM25 双路召回，使用 RRF(k=60)、可选 Rerank 和 Context Top 6；
- Vector、BM25、Fusion、Rerank、Context 均保存候选和阶段摘要，零结果、跳过和降级也可追溯；
- 仅检索指定 Workspace/知识库下各文档的当前版本，引用绑定文档版本、Chunk 和稳定来源位置；
- Vector、BM25、Rerank 分别使用当前可配置门槛 0.2、1.0、0.4；Context 同时执行 Top-K 与字符预算；
- Rerank 适配器校验数量、下标、重复项和非有限分数，瞬时故障显式降级到 Fusion；
- `/retrieval/search` 与 `/retrieval/debug` 可检查各阶段结果；
- RAG 使用版本化 Prompt、确定性无证据拒答、证据充分性契约和结构化 SSE；
- 助手消息具有 `STREAMING`、`COMPLETED`、`INTERRUPTED` 终态，显式停止会持久化部分正文并取消本实例模型流。

### Agent 首个切片（已实现，计算工具主路径已实测）

- Flyway V11 定义带 Workspace 复合外键的 `agent_runs`、`agent_steps`、`tool_executions`；Run 保存冻结配置、Deadline、取消意图与租约，Step 按序号记录尝试，Tool Execution 保存唯一调用与幂等身份；
- Run/Step 领域状态机、MyBatis 条件更新和事务服务已加入；同步 API 支持创建运行、查询和请求取消；
- `ChatModel` 适配器读取版本化 Agent Prompt 并返回原生结构化 Tool Call；Runtime 限制四轮、60 秒总 Deadline、20 秒单次外部操作、重复计算调用检测和协作式取消；
- `calculator` 是当前唯一允许工具；参数按其精确 JSON Schema 在代码中验证，工具响应进入下一轮模型上下文，执行摘要落库。
- 新增 Run/Step 状态机、calculator 参数边界和固定模型决策的 ReAct 循环单元测试；均尚未执行。运行配置记录 Prompt SHA-256、工具版本、模型参数、Deadline 与 `PBRAG_CODE_VERSION`（未设置时为 `dev`）。

### 文档与评测资产

- [RAG 数据库导览](RAG_DATABASE_WALKTHROUGH.md) 解释入库、检索、问答、表字段和故障分支；
- [面试指南](PB_RAG_INTERVIEW_GUIDE.md) 以已实现事实和验证边界组织生产问题；
- [评测索引](../evaluation/README.md) 说明语料、数据集、报告和复现边界；
- `retrieval-v1` 固定 4 个文档版本、377 个 Chunk、32 条可回答问题和 8 条不可回答问题；
- [正式基线与 Top 30/60 对照](../evaluation/reports/retrieval-v1-baseline-2026-11-19.md) 是当前唯一权威检索报告；早期 11 条人工抽样结论已并入该报告。

## 最近有效验证

- 2026-11-12，Microsoft OpenJDK 21.0.13 执行后端 `mvn test`：52 个测试中 51 个通过，1 个依赖 Testcontainers 的上下文测试跳过；
- 2026-11-18，直接连接 `application.yml` 指向的 PostgreSQL 18.6 / ParadeDB 开发库运行 `PostgreSqlReliabilityIntegrationTests`：10/10 通过，覆盖并发领取、租约恢复、事务原子性、并发版本号、永久失败、租约保护、取消竞争、BM25 作用域和复合外键；测试会清空业务数据，只能在 `PBRAG_ALLOW_EXTERNAL_DB_TESTS=true` 时执行；
- 2026-11-19，完整 `RetrievalServiceTests`：8/8 通过，覆盖 RRF、Rerank 排序与门槛、瞬时降级、协议错误、BM25 门槛、Context 预算和向量维度错误；
- 2026-11-19，本地 `openai` profile 的 Agent Run `e004eda4-49eb-4713-816a-ff42fddc1990`：同步 API 返回 `COMPLETED` 和答案 4；数据库只读核对依次为 `MODEL_CALL → TOOL_CALL(calculator, result=4) → MODEL_CALL → FINAL_RESPONSE`，四个 Step 与工具执行均为 `COMPLETED`；以另一个知识库 ID 查询同一 Run 返回 HTTP 404。这是一次主路径及作用域样本，不代表单元、并发、取消或恢复验证；
- 2026-11-19，`retrieval-v1` Top 60 全量运行 40/40 首次请求成功：Context Recall@1/3/6 为 91.18%/94.12%/100%，MRR 0.94；Rerank 平均/P50/P95/最大延迟为 569/352/1226/3439 ms；8/8 不可回答问题得到正确拒答；
- retrieval-031 的两份跨文档证据在 Top 30 被提前截断，在 Top 60 分别进入 Context 第 3/1，失败根因和修复方向均已得到同数据集验证；
- 前端最近一次验证已通过 type-check、lint 和生产构建；后续前端改动需用新结果覆盖此处。

## 当前决定与配置边界

- Chat/RAG：`qwen3.7-flash`；Embedding：`qwen3.7-text-embedding-flash`、1024 维；Rerank：`qwen3.7-text-rerank`；
- `application.yml` 的仓库默认 `fusion-top-k` 已固化为 60；部署环境仍应核对实际生效配置，配置提交后的小范围回归尚未执行；
- Prompt、模型别名、门槛、数据集、manifest 和代码提交都会影响可复现性，评测必须一并记录；
- Workspace 目前是共享知识分组而非授权边界；安全策略不能交给模型或 Prompt；
- Agent 生产主线采用 PB-RAG 领域 Runtime；MCP 是受治理的远程工具协议，Skill 是版本化业务能力包，LangGraph4j 仅用于对照实验。

## 开放风险

- `retrieval-v1` 只有 40 条，足以作为首版回归门槛，但仍需扩充难例、人工复核标注并补充引用准确率、Faithfulness、Token 与费用；
- Top 30 缺完整延迟分布，因此不能把两次远程运行差异解释为 Top 60 更快；应持续观察 Top 60 的生产 P95、最大延迟与成本；
- Rerank 0.4 和 BM25 1.0 是当前数据集上的首版门槛，新增语料、模型或问题分布后必须重新校准；
- 主题相关不等于证据蕴含。retrieval-033～035 依赖最终回答契约拒答，不能只用 Context 是否为空判定正确性；
- 组合引用被 Markdown 反引号包裹的展示瑕疵尚未专门修正；
- Redis 取消传播和数据库轮询兜底仍缺真实多实例验证；模型流中断、断连和页面刷新恢复还需持续做端到端故障回归；
- 远程 PostgreSQL 曾出现连接集中重置，当前连接保活只能加快发现与恢复，根因仍需结合服务器、网络和防火墙日志确认；
- 所有登录用户共享资源，开放注册仅适合受控开发环境；公网部署前必须增加正式身份与授权模型；
- 本地服务已启动且一次真实模型 `calculator` 主路径跑通；单元测试、Flyway 隔离迁移检查、取消/完成竞争、超时和重启恢复仍未验证。尚缺 SSE、轨迹查询、租约续期与恢复调度、审批、知识工具、Skill、MCP 和 LangGraph4j 对照；
- 当前工作区的 `application.yml` 存在未提交的配置修改，包含明文凭据；本轮没有改动该文件。提交或共享前必须移除明文值并轮换已暴露凭据；

## 下一步

1. 项目发起人按 `docs/RULES.md` 执行 Agent 状态机与 calculator 单元测试、Flyway V11 隔离迁移检查，再补多个固定计算样本；修正由验证暴露的问题；
2. 从同步切片扩展出轨迹查询、SSE、租约续期/重启恢复、工具瞬时故障有限重试和取消/完成竞争集成测试；
3. 接入 `search_knowledge`，将文档版本、Chunk 和来源位置绑定到 Agent Observation 与引用，再完成跨文档比较；
4. 回归 Top 60 的 retrieval-031、033～040 关键样本，确认默认配置没有漂移；随后建立 `technical-change-review` Skill v1 和 `agent-v1` 数据集；
5. 本地工具稳定后开展审批、MCP 和 LangGraph4j 对照。
