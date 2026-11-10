# PB-RAG 当前交接

> 这里只记录当前事实、风险和下一步，不保存聊天历史。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一阶段数据地基、文档上传、Java 解析/分块 Worker 与向量写入编排已完成。下一步确认百炼 Embedding 实际维度与接口配置，在真实 PostgreSQL 上验证 Worker、HNSW 迁移和端到端检索。

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
- 模型服务已形成暂定基线，但尚未完成账号、密钥、具体版本、Embedding 维度和接口连通性验证；
- RAG 固定评测数据集尚未建立；
- 当前没有 Workspace 成员授权；所有已登录用户共享资源，部署边界仍须限制可注册人群。
- 已有账号认证但没有 Workspace 成员授权；开放注册仅适合受控开发环境，公网部署前需注册限制、登录限流、密码重置与会话清理；
- 百炼官方文档确认 `qwen3.7-text-embedding-flash` 默认输出 1024 维、同步接口单批最多 20 条；代码配置与 V5 迁移已统一为 1024 维/20 条，但当前环境没有模型 API Key，尚未完成真实接口输出回测；
- Worker 的 PostgreSQL 领取、租约恢复、V4 HNSW 迁移与向量写入 SQL 已完成代码和 XML 语法验证，尚未通过 Testcontainers 或真实数据库集成测试。

## 下一步

完成模型确认与真实数据库闭环：

1. 配置百炼华北 2 OpenAI-compatible 地址、API Key 和模型 ID，用真实响应验证默认 1024 维及批量调用；
2. 在目标 PostgreSQL 上验证 Flyway V5 的维度迁移和 HNSW 重建；
3. 跑通上传、领取、解析、分块、向量写入与 Job 完成；
4. 增加 Testcontainers 覆盖 `SKIP LOCKED` 并发领取、租约过期、重复消费、V5 HNSW 迁移和资源归属；
5. 实现 Workspace 约束下的基础 Top-K 向量检索，再进入 Hybrid/Rerank。

## 模型接入提醒

进入 Embedding、RAG 回答或 Agent Runtime 实现前，按 `PROJECT_CONTEXT.md` 的“当前模型服务暂定基线”执行：

- 阿里云百炼华北 2；
- Chat/RAG 与 Agent 初始模型使用 `qwen3.7-flash`；
- Embedding 使用 `qwen3.7-text-embedding-flash`；
- 第二周接入 `qwen3.7-text-rerank`，通过独立适配器封装；
- Agent 质量不足时，再用固定评测集比较 `qwen3.7-plus` 与 `deepseek-flash`。

接入前继续核对当时的官方价格、具体模型版本和限流；配置密钥后用真实响应断言 1024 维，再运行完整入库闭环。

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
