# PB-RAG 当前交接

> 本文件只记录当前事实、验证、风险和下一步。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一周纵向 RAG 闭环和第二周 Production RAG 主链路已完成。系统已具备真实文档入库、百炼 Embedding、pgvector 与 ParadeDB BM25 双路召回、RRF、可选 Rerank、检索阶段记录、千问流式回答、版本化引用、会话持久化、刷新恢复和显式停止能力。

当前重点是完成 `retrieval-v1` 全量统一基线和本轮证据门槛/回答契约回归。PostgreSQL 自动化集成验证已经通过；全量评测通过后进入第三周 Agent Runtime。

Agent 阶段的稳定方案已由 ADR 0009 确定：生产主线由 PB-RAG 自行实现紧凑的 Run/Step 与 ReAct Runtime；ReAct 使用结构化 Tool Call，MCP 作为受治理的远程工具协议，Skill 作为随仓库发布的版本化业务能力包。LangGraph4j 不进入首期生产依赖，只在基线稳定后以相同 Prompt、工具契约和数据集进行对照评测。

## 已完成能力

- `docs/RAG_DATABASE_WALKTHROUGH.md` 提供入库、检索、问答与数据库流转导览，新增带流动阶段、重试与降级分支的“上传到 Embedding / Vector 到 Rerank”双链路动画总览，并保留 Mermaid、分主题 SVG、字段样例、源码链接和只读观察 SQL；示例数据仅用于教学。
- `docs/PB_RAG_INTERVIEW_GUIDE.md` 基于当前实现和验证边界整理生产面试问题与回答框架，覆盖召回不准的分阶段诊断、版本与任务一致性、性能成本、中国企业合规环境、Agent 安全、故障场景和待准备证据；不把未执行的评测与集成测试描述为已验证结论。

### 工程与资源模型

- Spring Boot 4.1.2 / Java 21 后端与 Vue 3 / TypeScript 前端骨架；
- PostgreSQL、pgvector、ParadeDB、Redis、Flyway 和 MyBatis-Plus 分层；
- Workspace、知识库、文档、版本、Chunk、入库 Job、会话、消息和引用模型；
- Workspace 与知识库创建、列表、详情，文档上传/更新、列表、Job 查询与主动取消；
- 邮箱注册、登录、退出和个人资料；所有已登录用户仍共享 Workspace；
- `X-Request-Id`、Trace/Span、Workspace MDC、结构化访问日志和滚动文件日志；
- ArchUnit 约束 Controller/Service/Mapper 与 JDBC 边界。
- V10 为当前全部业务表和字段补充 PostgreSQL 中文注释，覆盖资源作用域、文档版本、Chunk 来源位置、入库状态与租约、检索阶段分数、问答消息和引用快照等易混淆语义；注释仅修改数据库元数据，不改变表结构或数据。

### 文档入库

- Markdown 与普通文本 PDF 解析；
- 流式 SHA-256、本地对象存储、事务创建和失败补偿；
- 同知识库相同内容幂等返回已有版本与 Job；
- 标题感知分块及字符偏移、Markdown 行号、标题路径和 PDF 页码；
- 分块目标长度与最大长度仅由 `application.yml` 的 `pbrag.ingestion.chunking` 配置提供，代码不再维护重复默认值，缺失配置会在启动阶段显式失败；
- PostgreSQL `FOR UPDATE SKIP LOCKED` 领取、租约、过期恢复、有限重试和结构化失败；
- Worker 按稳定错误分类区分可重试与永久失败：Embedding 429、超时及临时连接错误有限重试，不支持格式、无可提取文本、非法输入和向量维度错误直接失败；失败状态条件更新会检查受影响行数，丢失租约时不误报已落库；最后一个远程 Embedding 批次返回后会在向量落库前再次续租，防止已被恢复的旧 Worker 覆盖新 Worker 结果；
- 1024 维 Embedding 校验、批量写入和 HNSW cosine 索引；
- 状态迁移在领域层和数据库条件更新两层约束。
- 已新增直接连接 `application.yml` 数据源的 PostgreSQL/ParadeDB 集成测试，覆盖两个 Worker 并发领取同一任务时仅一个成功、过期租约恢复后再次领取、丢失租约时任务完成与当前版本切换整体回滚、同一文档并发创建版本号、首次永久失败、失败落库时租约丢失，以及取消与完成竞争；测试会清空全部业务数据，仅在 `PBRAG_ALLOW_EXTERNAL_DB_TESTS=true` 时执行。
- 已有文档支持 `POST /api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/documents/{documentId}/versions` 上传同媒体类型的新版本；文档行锁保证版本号递增与并发一致性，重复内容复用已有版本，新版本入库成功后才与 Job 完成状态同事务切换为当前版本。
- 入库任务支持 `POST /api/workspaces/{workspaceId}/ingestion-jobs/{jobId}/cancel`；PostgreSQL 条件更新保存 `CANCELLED/CLIENT_CANCELLED` 终态，Redis TTL 标记让 Worker 在阶段与 Embedding 批次边界快速停止；
- 新任务创建后通过 Redis Pub/Sub 广播唤醒所有实例，实例收到信号后仍通过 PostgreSQL `SKIP LOCKED` 领取；Redis 故障时定时数据库轮询兜底，不改变任务事实来源；
- Redis Pub/Sub 监听由 `PBRAG_INGESTION_REDIS_SIGNALS_ENABLED` 控制；监听容器不参与 ApplicationContext 自动启动，连接失败由后台每 10 秒重试，PostgreSQL 轮询继续工作；宝塔 Redis 当前无密码，数据库与模型凭据只允许通过环境变量提供；

### Production RAG 与问答

- pgvector 与 ParadeDB BM25（`chinese_lindera`）各召回 Top 30；
- RRF(k=60)、独立 `RerankClient`、瞬时故障降级和最终 Top 6 上下文；
- `retrieval_runs` / `retrieval_candidates` 保存 VECTOR、KEYWORD、FUSION、RERANK、CONTEXT 阶段；
- V9 增加 `retrieval_stage_summaries`，保存零结果、跳过和降级阶段的状态、候选数、耗时与错误码；运行记录保存实际最终阶段和降级原因；
- `/retrieval/search` 与 `/retrieval/debug` 支持检索及阶段快照查看；
- 前端知识库详情页提供检索调试入口，可查看阶段状态、候选数、延迟、错误码、排名、分数、来源位置与元数据；`RERANK_DISABLED` 和瞬时降级不会被 UI 隐藏；
- 只检索当前文档版本，并返回 Chunk、版本、稳定来源位置和分数；
- 外部数据库集成测试同时覆盖 ParadeDB BM25 只返回指定 Workspace、知识库的当前版本 Chunk，以及复合外键拒绝跨 Workspace Chunk 关联；相关测试已在真实开发库通过。
- BM25 使用可配置最低证据分数 `1.0`；Rerank 新增首版最低证据分数 `0.4`，过滤前数量、门槛和过滤后数量进入阶段摘要；最终 `CONTEXT` 同时执行 Top-K 与字符预算裁剪，因此阶段记录等于真实 Prompt 证据；
- 百炼 Rerank 配置独立连接/读取超时，校验结果数量、下标、重复项和非有限分数，瞬时失败显式降级到 FUSION；
- 确定性无证据拒答、版本化 Prompt、结构化 SSE 和受限引用原文摘录；
- 会话、消息和引用按当前用户持久化，刷新后可恢复；
- 助手消息有 `STREAMING`、`COMPLETED`、`INTERRUPTED` 明确终态；
- 显式停止 API 在完整用户与资源作用域内持久化部分正文并取消本实例模型流；连接断开作为兜底。
- Prompt 元数据解析兼容 LF、Windows CRLF 和可选 UTF-8 BOM，避免不同 Git 换行配置导致应用启动失败。
- 文档页支持针对已有逻辑文档上传新版本、观察新版本 Job，并主动取消未完成任务；新版本完成后自动刷新当前版本。

### 评测资产

- `evaluation/corpus/retrieval-v1-manifest.json` 冻结 4 个当前文档版本、377 个已完成 Embedding 的 Chunk；
- `evaluation/datasets/retrieval-v1.jsonl` 包含 40 条人工真值草案，其中 32 条可回答、8 条拒答；
- `retrieval-033` 的损坏 JSONL 已恢复为“系统是否支持通过短信发送知识文档审批结果？”；40 行现在均应保持一行一个合法 JSON 对象；
- `evaluation/reports/retrieval-v1-manual-smoke-2026-11-18.md` 固化 8 条可回答、3 条不可回答的人工抽样，记录排名、低相关误召回和证据蕴含失败；证据绑定文档版本、Chunk 和稳定来源位置，尚未完成 40 条正式基线。
- `evaluation/reports/retrieval-v1-baseline-2026-11-19.md` 记录 40 条单并发全量运行：Context Evidence Recall@1/3/6 为 88.24%/88.24%/94.12%，MRR 0.90；8 条不可回答样本均得到正确拒答语义。retrieval-031 的两份跨文档期望证据被 RRF Top 30 挤出，最终只能回答 PRD 部分，因此该报告状态为“待优化”，尚未达到 Agent Runtime 准入。

## 当前关键配置与决定

- Chat/RAG：`qwen3.7-flash`；Embedding：`qwen3.7-text-embedding-flash`，1024 维；
- Rerank：`qwen3.7-text-rerank`，默认关闭；配置业务空间专属 URL 与 API Key 后已有单样本真实调用成功，正式评测仍需显式启用；
- PostgreSQL Job 表是任务事实来源，Redis 只负责唤醒、取消标记和限流；
- Flyway 是唯一 DDL 管理方式，业务访问统一经过 MyBatis-Plus Mapper；
- Workspace 是共享知识资源分组，不是授权边界；
- Docker 一键启动和 CI 暂低于 Production RAG、Agent Runtime 与评测闭环优先级。

## 验证状态

- 2026-11-12 使用 Microsoft OpenJDK 21.0.13 执行后端 `mvn test`：共 52 个测试，51 个通过，1 个 Spring 上下文测试因未接入 Testcontainers 跳过；113 个主源码文件和 20 个测试源码文件均完成编译；
- 2026-11-19 使用 Microsoft OpenJDK 21.0.13 在 IDE 运行完整 `RetrievalServiceTests`：8 个测试全部通过，覆盖 RRF 去重、Rerank 排序与最低证据分数、瞬时故障降级、协议错误、BM25 门槛、Context 字符预算和向量维度失败终态；日志中的 `rerank_degraded` 是故障用例预期输出。
- 入库单元测试覆盖永久/瞬时错误分类，以及已有文档递增版本创建且处理完成前不切换当前版本；
- ParadeDB 开发库为 PostgreSQL 18.6、`pg_search` 0.26.6；Flyway V1～V10 已应用，BM25 查询使用 `Custom Scan (ParadeDB Base Scan)`，错误 Workspace 返回 0；
- 真实 PostgreSQL 已完成 Spring 上下文启动和 Flyway V1～V10 校验；
- 前端最近已通过 `type-check`、`lint` 和生产构建；
- 全部 Mapper XML 已通过 XML 格式解析检查，`git diff --check` 通过；
- 显式停止闭环、Prompt CRLF + BOM、检索阶段追溯、BM25 证据门槛、Context 裁剪和 Rerank 超时的现有回归测试均已在最近一次全量测试通过；2026-11-18 使用 `qwen3.7-text-rerank` 完成 `retrieval-001` 单样本真实调用，Rerank Top 10 成功、耗时 539 ms、期望 Chunk 重排后保持第 1，最终阶段为 RERANK 且无降级。
- 文档版本前端入口、检索调试页、入库主动取消与 Redis 唤醒/取消信号已实现；按协作规则尚未执行本轮后端编译、测试和前端类型检查，不能视为已验证完成。
- 数据库中文注释迁移 V10 已在真实开发库应用；数据库客户端需要刷新 Schema 元数据才能看到注释。
- 2026-11-18 使用 Microsoft OpenJDK 21.0.13 在 `application.yml` 指向的 PostgreSQL 18.6/ParadeDB 开发库执行 `PostgreSqlReliabilityIntegrationTests`：10 个测试全部通过，无失败、错误或跳过；Flyway 成功校验 10 个迁移，116 个主源码文件和 22 个测试源码文件完成编译。测试覆盖并发领取、租约恢复、完成事务原子性、并发版本号、永久失败、失败更新租约保护、取消/完成竞争、BM25 资源作用域和复合外键。
- 远程 PostgreSQL 连接增加 JDBC TCP keepalive、5 秒建连超时和 Hikari 保活/连接寿命配置；RAG 在数据库断连导致中断状态无法落库时仍保留原始结构化错误，遗留 `STREAMING` 由会话恢复逻辑收敛。已补回归测试，按协作规则尚未执行。

以上是最近一次有效验证快照。后续运行应以新结果覆盖本节，不追加历史流水。

## 当前风险

- `retrieval-v1` 的 40 条真值仍是草案；在人工复核和正式运行前，不能宣称 BM25、RRF 或 Rerank 带来质量提升；
- BM25 最低分数 `1.0` 只是首版保守基线，必须通过可回答与拒答样本共同校准，避免过度召回或误拒答；
- Rerank 最低分数 `0.4` 来自 11 条人工抽样，只能过滤低相关误召回；短信能力这类“相关但不蕴含答案”的样本仍必须在最终回答层验证拒答；
- `qwen3.7-text-rerank` 已完成 11 条人工抽样，但尚未运行 40 条正式基线；限流、P95 延迟、成本和真实故障降级仍需记录；
- 2026-11-19 使用 `minimum-rerank-score=0.4` 回归 retrieval-036/040：Rerank 均由 10 个原始候选过滤为 0，Context 为空；retrieval-033 保留相关站内信证据后，Prompt 1.1.0 最终回答明确说明资料未记载短信支持并进入 `completed`，证据充分性拒答验证通过。组合引用被 Markdown 反引号包裹的展示瑕疵仍待后续 Prompt 微调；
- 2026-11-19 全量运行中 retrieval-039 首次调用返回 HTTP 500，重试后成功；Rerank 平均阶段耗时约 616.6 ms，观察到最大 8,373 ms，尚缺持久化延迟分布和 P95；
- retrieval-031 的 `fusion-top-k=60` 单样本实验成功：两份期望证据由 Fusion 第 50/53 提升到 Rerank 与 Context 第 3/1，确认 Top 30 提前截断是根因；但该样本 Rerank 耗时由约 363 ms 增至 2,383 ms，必须完成 40 条延迟与拒答回归后才能固化配置；
- Redis 取消信号的传播速度和 Redis 故障时的 PostgreSQL 轮询兜底仍缺真实多实例集成验证；
- 停止、模型流中断、断连和页面刷新恢复需要在真实服务与真实文档上继续回测；
- 2026-11-13 的一次故障中多个 PostgreSQL 连接在约 11 秒内先后关闭并最终同时 `Connection reset`；现有证据指向数据库重启、网络/NAT 或代理中断，而非业务 SQL。连接参数只能减少半开连接复用并加快恢复，仍需结合故障时刻的 PostgreSQL、系统和云防火墙日志确认外部触发源；
- 所有已登录用户共享资源；开放注册只适合受控开发环境，公网部署前需要注册限制、登录限流、密码重置、会话清理和正式授权模型；
- 当前模型别名可能自动升级，评测必须记录可获得的具体模型版本和运行配置。
- Agent Runtime、MCP 和 Skill 尚未实现；ADR 0009 只确定边界和实施顺序，不能描述为已有能力；
- LangGraph4j 对照尚未建立，当前不能宣称自研编排在质量、可靠性或成本上优于框架方案；

## 下一步

1. 使用 `fusion-top-k=60` 对相同 40 条数据做 A/B 回归，比较 Top 30 与 Top 60 的 Recall、拒答、平均延迟与 P95；
2. 要求 retrieval-031 两份证据进入 Context Top 6，整体 Recall@6 不低于 94.12%，8 条不可回答样本继续正确拒答；若延迟成本不可接受，将显式跨文档任务交给 Agent `compare_documents`；
3. 将逐条阶段指标、错误、延迟和配置写入版本化结果，补齐 P50/P95、Git commit、数据集/manifest SHA-256、模型版本与费用；
4. 固化正式基线报告后开始 Agent Run/Step 状态机；
5. Agent Runtime 开始后并行补充 Redis 取消信号的多实例验证，不让该 P1 场景阻塞 Run/Step 领域骨架；
6. 按 ADR 0009 实现 Agent Run/Step、结构化 ReAct 循环和显式 Tool Registry；
7. 先实现 `search_knowledge`、`read_document`、`list_documents`、`compare_documents` 与 `calculator`，再建立 `technical-change-review` Skill v1；
8. 本地工具稳定后实现只读 MCP Server 和一个受控 MCP Client 工具，最后以相同契约建立 LangGraph4j 对照实验。
