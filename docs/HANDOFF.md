# PB-RAG 当前交接

> 这里只记录当前事实、风险和下一步，不保存聊天历史。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一周纵向闭环代码已完成：真实文档入库、百炼 Embedding、pgvector Top-K、千问流式回答、引用原文、会话/消息/引用持久化和刷新恢复均已接入。下一步先在真实数据库应用 V6 并回测正常完成、中断、停止和刷新恢复，再进入 Hybrid、Rerank 与评测。

外部 PostgreSQL、pgvector 与 Redis 已部署。当前代码已在 JDK 21 下通过编译和单元测试，并在真实 PostgreSQL 上成功启动；Flyway V3 已应用。尚未通过 Testcontainers 自动验证完整 Spring 上下文、Flyway 与 Mapper SQL。

## 已完成

- Spring Boot 4.1.2、Java 21、Spring AI 2.0.1 后端骨架；
- Vue 3、TypeScript、Vite、Pinia、TDesign 前端骨架；
- PostgreSQL、pgvector、Redis 外部连接配置；
- Flyway V1 启用 vector，V2 创建 Workspace、知识库、文档、版本、Chunk 和入库 Job；
- Workspace 与知识库创建、列表、详情 API；
- 统一 `ApiResponse<T>`、404、409 和参数错误响应；
- 入库 Job 状态机及其领域测试；
- MyBatis-Plus 与标准 `Controller → Service → ServiceImpl → Mapper → Mapper.xml` 分层；
- 单用户范围：暂缓多租户、成员关系和 RBAC，Workspace 仅作为知识资源分组。
- 前端已对齐当前 Workspace 与知识库 API：支持 Workspace 列表/创建、知识库列表/创建/详情、统一错误展示和加载/空状态；
- Vite 开发代理已与后端 `8088` 端口对齐，知识库详情页已接入 Markdown 上传和 Job 状态展示；
- Markdown 上传支持文件类型/大小/空文件校验、流式 SHA-256、本地对象存储和失败补偿；
- Document、DocumentVersion 与 IngestionJob 在同一事务中创建，同知识库相同 Hash 返回已有任务；
- 提供 Workspace 作用域内的 Job 查询 API，前端上传后每 2 秒轮询并展示阶段、尝试次数和结构化错误。
- 提供知识库作用域内的文档列表 API，返回当前版本及对应入库 Job；前端详情页刷新后会从数据库恢复文档列表，并继续轮询未结束任务；
- 前端布局增加常驻知识库侧栏，可在文档详情间直接切换；详情页增加文档状态概览、名称搜索与更紧凑的列表，技术标识折叠展示；
- 工作区选择首页改为完整工作台入口，侧栏增加品牌区、分组、知识库数量与当前工作区信息；知识库卡片和小屏布局已统一调整；
- 首页已收敛为简洁工作区入口；新增邮箱注册、登录、退出与本人显示名称修改，API 需要服务端会话；所有已登录用户仍共享工作区，见 ADR 0005；
- 注册密码长度为 6 到 128 个字符；
- 登录注册页改为简洁的双栏布局，说明当前共享范围，窄屏自动改为单栏；
- 登录注册页已补齐字段级校验、内联服务端错误、提交中状态、登录失效后返回原访问路径和已登录自动跳转；桌面与移动端视觉层级、键盘焦点及共享 Workspace 提示已统一优化；
- API 日志体系已建立：`X-Request-Id`、MDC、Workspace 关联、状态码/耗时/错误码访问日志和滚动文件；控制台采用精简格式，完整关联字段保留在文件日志；
- 已接入 Micrometer Tracing 的 OpenTelemetry bridge，HTTP Trace ID/Span ID 自动关联日志；远程 OTLP exporter 尚未配置；
- `local` Profile 可开启 MyBatis Mapper SQL 日志，默认环境避免输出 SQL 参数和敏感内容；
- 入库创建与重复上传记录结构化业务事件，不记录文档正文或完整内容 Hash。
- Java `DocumentParser` 已支持 CommonMark Markdown 与普通文本 PDF（PDFBox）；扫描件、复杂表格和 OCR 明确留给后续远程解析适配器；
- 标题感知分块策略保存字符偏移、Markdown 行号、标题路径和 PDF 页码，Chunk 按文档版本原子替换并记录解析/分块版本；
- Worker 使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 领取任务，支持租约、过期恢复、有限重试和结构化失败；只有存在 `EmbeddingModel` 时才消费任务，避免未配置模型时伪造向量；
- Embedding 写入前校验输出维度，Flyway V4 为 `chunks.embedding` 增加 HNSW cosine 索引；前端上传入口已同时接受 Markdown 与普通 PDF。
- 已接入阿里云百炼 OpenAI-compatible 接口：Chat 使用 `qwen3.7-flash`，Embedding 使用 `qwen3.7-text-embedding-flash`；真实文档入库任务已完成，1024 维校验和向量写入通过 Worker 完成条件验证；
- 新增 Workspace/知识库双重约束的 pgvector Top-K 检索，只查询文档当前版本并返回 Chunk、文档版本、来源位置和相似度；
- 新增最小 RAG 闭环：确定性无证据拒答、版本化 Prompt、千问流式回答、结构化 SSE 事件和引用侧栏；文档内容按不可信数据处理，不允许覆盖系统规则。
- 知识问答入口已提升到工作台侧栏一级，问答页负责选择知识库；知识库详情页不再承载问答入口。
- 修复真实 RAG 请求中 MyBatis 将 SQL `Integer`/`Double` 错配到 `RetrievalHit` 原始类型构造器导致的 500；检索阶段异常现在也会作为结构化 SSE 错误返回。
- SSE 客户端主动停止或离开页面导致连接断开时，后端不再尝试向已提交的 `text/event-stream` 响应写入 JSON `ApiResponse`，避免 `HttpMessageNotWritableException` 二次异常。
- 知识问答前端改为多轮会话式交互，逐轮展示问题、生成状态、回答与引用，支持停止、失败重试、清空和引用切换；已有部分正文时将流异常标记为“生成中断”并保留内容，不再显示整次问答失败。有限 SSE 的传输异常不再自动重复 POST。
- RAG 错误协议区分检索失败 `RAG_RETRIEVAL_FAILED` 与模型流中断 `RAG_GENERATION_INTERRUPTED`，便于前端给出准确恢复操作。
- 修复千问流末尾元数据帧被误判为生成中断：模型返回无文本的完成/用量帧时直接忽略，不再让 Reactor `map` 因空值抛出 `NullPointerException`。
- 后端启动类通过 Spring Boot `Banner` API 输出 PB-RAG ASCII 标识、Knowledge Agent 副标题和当前 Spring Boot 版本，不依赖额外资源文件。
- RAG 从仅流式输出正文扩展为全链路阶段反馈：请求后立即发送“正在检索”，检索完成发送证据数量，再增量输出模型正文；Embedding/数据库检索调度到 bounded-elastic，避免阻塞 SSE 写出线程。
- 引用侧栏现展示最多 500 字符的安全原文摘录，并继续保留文档名、Chunk、版本、来源位置和相似度；
- 新增 Flyway V6 与 `chat_sessions`、`chat_messages`、`message_citations`：会话绑定当前用户和知识库，每轮问题/回答使用 `turn_id` 配对，引用绑定助手消息；
- 流式助手消息从 `STREAMING` 进入 `COMPLETED` 或 `INTERRUPTED`，正常完成保存全文，客户端停止或模型异常保留部分正文；刷新页面可通过 session 查询恢复历史消息与引用，服务重启遗留的流式消息会收敛为中断状态。

## 当前关键决策

- Spring AI 只提供模型、Embedding、工具 Schema、向量基线与观测；Agent Runtime、Production RAG、任务状态、引用和评测由本项目实现；
- Flyway 是唯一 DDL 管理方式；MyBatis-Plus 不自动建表；
- 简单写入可使用 `BaseMapper`，定制查询及 pgvector、FTS、RRF、`SKIP LOCKED`、批处理 SQL 放在 Mapper XML；
- PostgreSQL Job 表是任务事实来源，Redis 只负责唤醒、取消标记和限流；
- 首期在 Java 进程内解析 Markdown 和普通 PDF；
- 不实现多租户和 RBAC，但保留 `workspace_id` 与复合外键确保资源归属一致。

## 当前风险

- 后端已在 JDK 21 下通过编译与单元测试，但 Spring 上下文及 Mapper SQL 尚未通过真实 PostgreSQL 自动化集成测试；
- Flyway V2 尚未纳入自动化 PostgreSQL 集成测试；
- 百炼账号、API Key、OpenAI-compatible 地址、Chat 与 Embedding 模型已经接通；仍需用固定评测集记录具体模型版本、质量、延迟和成本基线；
- RAG 固定评测数据集尚未建立；
- 当前没有 Workspace 成员授权；所有已登录用户共享资源，部署边界仍须限制可注册人群。
- 已有账号认证但没有 Workspace 成员授权；开放注册仅适合受控开发环境，公网部署前需注册限制、登录限流、密码重置与会话清理；
- `qwen3.7-text-embedding-flash` 已按 1024 维、单批最多 20 条运行，真实入库任务已完成；后续模型或维度变更仍必须通过新迁移和索引版本处理；
- Worker 的 PostgreSQL 领取、租约恢复、V4 HNSW 迁移与向量写入 SQL 已完成代码和 XML 语法验证，尚未通过 Testcontainers 或真实数据库集成测试。
- Flyway V6 新增的问答会话、消息与引用表尚待本次代码启动后在真实开发库执行，并回测流中断和页面刷新恢复。

## 下一步

完成第一周 RAG 闭环并建立第二周检索基线：

1. 使用已完成入库的真实文档回测 Top-K 检索、SSE 回答、无证据拒答和版本级引用；
2. 建立首批 30～50 条固定问题、期望证据和拒答样本，记录 Recall@K、引用准确率、延迟和模型配置；
3. 增加 PostgreSQL 全文关键词检索，与向量结果通过 RRF 融合；
4. 通过独立适配器接入 `qwen3.7-text-rerank`，保存向量、关键词、融合和重排的中间结果；
5. 数据库自动化集成验证暂不阻塞当前闭环，完成基础 RAG 后再补 `SKIP LOCKED`、租约恢复、迁移和资源归属测试。

Docker 一键启动完整环境和 CI 流水线按当前决定降为最低优先级，放到核心 Production RAG、Agent Runtime 与评测闭环之后处理，不阻塞当前迭代。

## 模型接入提醒

进入 Embedding、RAG 回答或 Agent Runtime 实现前，按 `PROJECT_CONTEXT.md` 的“当前模型服务暂定基线”执行：

- 阿里云百炼华北 2；
- Chat/RAG 与 Agent 初始模型使用 `qwen3.7-flash`；
- Embedding 使用 `qwen3.7-text-embedding-flash`；
- 第二周接入 `qwen3.7-text-rerank`，通过独立适配器封装；
- Agent 质量不足时，再用固定评测集比较 `qwen3.7-plus` 与 `deepseek-flash`。

当前百炼模型已经接入。进入评测前继续记录当时的官方价格、具体模型版本、限流和数据处理条款，避免只记录可能自动升级的模型别名。

## 验证状态

- `git diff --check`：已通过；
- Mapper XML：已完成 XML 语法解析检查；
- 后端 `mvnw test`（JDK 21）：16 个测试，15 个通过，Spring 上下文测试因尚未接入 Testcontainers 跳过；
- 后端 `mvnw spring-boot:run`：真实 PostgreSQL 上启动成功；未登录访问 `/api/workspaces` 返回 401 和统一错误结构；
- 前端 `npm run type-check`：已通过；
- 前端 `npm run build-only`：已通过，存在 TDesign 全量注册导致的主包体积警告；
- 前端 `npm run lint`：已通过。
- 登录注册 UI：前端类型检查、lint 与独立输出目录生产构建已通过，并在本地浏览器完成桌面端、390px 窄屏、登录空表单校验及登录/注册模式切换检查；常规 `dist` 构建曾因已有 Vite 进程占用 `dist/favicon.ico` 报 `EBUSY`，改用独立输出目录验证成功。
- Java 解析/分块与 Worker：`mvnw test`（JDK 21）20 个测试，19 个通过、1 个既有 Spring 上下文测试跳过；前端 `npm run type-check` 与 `npm run lint` 通过。
- 知识问答入口与检索映射修复：前端 `npm run type-check`、`npm run build-only` 通过；后端 JDK 21 `mvnw test` 通过（34 个测试，33 个通过、1 个既有 Spring 上下文测试跳过），随后新增检索异常 SSE 回归测试。
- SSE 断开异常处理：JDK 21 定向运行 `ApiExceptionHandlerTests` 与 `RagChatServiceTests`，3 个测试全部通过。
- 问答交互与错误语义优化：前端 `npm run type-check`、`npm run build-only` 通过；JDK 21 定向运行 `RagChatServiceTests` 与 `ApiExceptionHandlerTests`，4 个测试全部通过。
- 千问末尾空文本帧修复：JDK 21 定向运行 `RagChatServiceTests`，4 个测试全部通过，包含“正文帧 + 元数据空帧 + completed”回归场景。
- 全链路流式阶段反馈：JDK 21 定向运行 `RagChatServiceTests`，4 个测试全部通过；前端 `npm run type-check`、`npm run build-only` 通过。

## 最新修复

- 后端方法注释标准已进一步收紧：Controller 端点、应用服务接口与实现、Mapper 自定义 SQL、解析/分块/存储接口等显式方法均需独立说明其功能；Javadoc 统一放在 Spring/MyBatis 注解之前，便于 IDEA 正确关联到方法。简单访问器说明返回字段，核心方法额外记录作用域、事务、幂等、租约或安全语义。
- 统一日期时间规范：数据库继续使用 `TIMESTAMPTZ`，API 明确输出 ISO 8601 且不使用数字时间戳，前端新增统一工具按 `Asia/Shanghai` 展示 `yyyy-MM-dd HH:mm:ss`；日志继续保留毫秒和时区用于追踪；
- 修复百炼 Embedding 请求 `404: Unknown`：Spring AI 2 的 OpenAI Java Client 要求 Base URL 包含 `/v1`，默认地址已调整为 `https://dashscope.aliyuncs.com/compatible-mode/v1`；API Key 恢复为纯环境变量引用，禁止写入 YAML；
- 修复真实 PostgreSQL 写入 Markdown Chunk 时 `jsonb_build_object` 无法推断空页码参数类型：`source_position` 的整数与文本参数现在显式 CAST；同时关闭 OpenAI Profile 下多余的 Spring AI PgVectorStore，避免其初始化默认 `vector_store` 表，PB-RAG 继续独立维护 `chunks` 与后续 Production RAG；
- `application.yml` 现默认激活 `openai` Profile，IDEA 直接启动即可创建 EmbeddingModel 与入库 Worker；无 API Key、只运行非模型功能时需临时注释 `spring.profiles.active`；
- 修复入库任务永久停留 `PENDING/PENDING`：`IngestionWorker` 不再通过普通组件上的 `@ConditionalOnBean(EmbeddingModel.class)` 依赖自动配置注册顺序，改为由 `spring.ai.model.embedding=openai` 显式启用；Profile 已开启但模型 Bean 创建失败时应用会明确启动失败，不再静默缺失 Worker；
- Spring AI PgVectorStore 已关闭自动 Schema 初始化，Flyway 保持唯一 DDL 管理入口；新增 V5 将 `chunks.embedding` 从历史 1536 维迁移到 `qwen3.7-text-embedding-flash` 默认 1024 维并重建 HNSW，批量大小同步降为官方上限 20；
- 生产入库链路现已实际调用 `IngestionJobState`：领取校验 `START_PARSING`，Worker 阶段推进、完成、失败/重试先执行领域迁移，再由 Mapper 使用期望阶段和租约条件原子更新；
- 文档上传使用 try-with-resources 关闭 Multipart 输入流；本地对象存储优先原子移动，不支持 `ATOMIC_MOVE` 的文件系统自动退化为同目录普通移动；
- 后端 `mvnw test`：30 个测试，29 个通过、1 个既有 Spring 上下文测试跳过；新增输入流关闭与任务领取状态校验回归测试；
- 已完成当前生产代码注释审计：81 个后端 Java 文件均具备职责说明，核心入库 Worker、上传补偿、幂等创建、任务租约、解析定位、标题分块、向量维度校验、认证与安全流程补充了设计原因、边界和失败语义；22 个前端 TypeScript/Vue 文件均标明页面、Store、API 或安全处理职责，复杂轮询、SSE 与不可信 Markdown 路径补充行为说明；
- 账号认证持久层已从 `JdbcTemplate` 全量迁移到 `UserAccountService` 接口、`UserAccountServiceImpl`、MyBatis-Plus `UserAccountMapper`/`UserSessionMapper` 与 Mapper XML；注册、登录、有效会话查询、资料更新和退出均遵守统一分层；
- 账号核心安全流程已补充说明事务原子性、防邮箱枚举、令牌只存摘要、PBKDF2 参数升级和恒定时间比较原因的注释；简单领域对象与 Mapper 使用轻量功能注释；
- 新增 ArchUnit 持久层架构测试，禁止业务代码直接依赖 Spring JDBC/`java.sql`、Controller 直连 Mapper、Domain 反向依赖应用层；新增账号服务单元测试验证输入规范化、密码散列和会话令牌摘要；
- 后端 `mvnw test`：27 个测试，26 个通过、1 个既有 Spring 上下文测试跳过；3 条架构规则全部通过；
- 修复 Spring Boot 4 启动失败：认证过滤器不再依赖不存在的 Jackson 2 `ObjectMapper` Bean；
- 启动日志确认 MyBatis 解析显式 ResultMap 时缺少 Java UUID 到 PostgreSQL uuid 的 TypeHandler；
- 新增全局 `PostgreSqlUuidTypeHandler`，并通过 `ConfigurationCustomizer` 在 Mapper XML 解析前注册；
- UUID TypeHandler 与本轮新增代码已通过 JDK 21 编译；真实 PostgreSQL 启动验证仍待 Testcontainers 集成测试覆盖。
