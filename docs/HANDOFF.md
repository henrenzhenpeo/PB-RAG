# PB-RAG 当前交接

> 这里只记录当前事实、风险和下一步，不保存聊天历史。稳定目标与架构见 `PROJECT_CONTEXT.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 当前阶段

第一阶段数据地基、Markdown 上传原子创建和 Job 状态查询已完成，下一步进入 Worker 领取、Markdown 解析与分块。

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

## 下一步

实现 Worker 领取、Markdown 解析与分块：

1. 使用 PostgreSQL `FOR UPDATE SKIP LOCKED` 领取任务并写入租约；
2. 从对象存储读取 Markdown，生成带稳定来源位置的 Chunk；
3. 阶段变更通过入库状态机并持久化；
4. 覆盖重复消费、处理中崩溃、租约过期、重试和取消；
5. 增加 PostgreSQL Flyway、资源归属、上传并发幂等和 Job 查询集成测试；
6. Worker 跑通后再按模型提醒确认 Embedding 维度并进入向量化。

## 模型接入提醒

进入 Embedding、RAG 回答或 Agent Runtime 实现前，按 `PROJECT_CONTEXT.md` 的“当前模型服务暂定基线”执行：

- 阿里云百炼华北 2；
- Chat/RAG 与 Agent 初始模型使用 `qwen3.7-flash`；
- Embedding 使用 `qwen3.7-text-embedding-flash`；
- 第二周接入 `qwen3.7-text-rerank`，通过独立适配器封装；
- Agent 质量不足时，再用固定评测集比较 `qwen3.7-plus` 与 `deepseek-flash`。

接入前先核对当时的官方价格、模型版本、限流和 Embedding 维度；不要直接沿用 `chunks.embedding vector(1536)`，除非已验证与所选模型配置一致。

## 验证状态

- `git diff --check`：已通过；
- Mapper XML：已完成 XML 语法解析检查；
- 后端 `mvnw test`（JDK 21）：16 个测试，15 个通过，Spring 上下文测试因尚未接入 Testcontainers 跳过；
- 后端 `mvnw spring-boot:run`：真实 PostgreSQL 上启动成功；未登录访问 `/api/workspaces` 返回 401 和统一错误结构；
- 前端 `npm run type-check`：已通过；
- 前端 `npm run build-only`：已通过，存在 TDesign 全量注册导致的主包体积警告；
- 前端 `npm run lint`：已通过。
- 登录注册 UI：前端类型检查、lint 与独立输出目录生产构建已通过，并在本地浏览器完成桌面端、390px 窄屏、登录空表单校验及登录/注册模式切换检查；常规 `dist` 构建曾因已有 Vite 进程占用 `dist/favicon.ico` 报 `EBUSY`，改用独立输出目录验证成功。

## 最新修复

- 修复 Spring Boot 4 启动失败：认证过滤器不再依赖不存在的 Jackson 2 `ObjectMapper` Bean；
- 启动日志确认 MyBatis 解析显式 ResultMap 时缺少 Java UUID 到 PostgreSQL uuid 的 TypeHandler；
- 新增全局 `PostgreSqlUuidTypeHandler`，并通过 `ConfigurationCustomizer` 在 Mapper XML 解析前注册；
- UUID TypeHandler 与本轮新增代码已通过 JDK 21 编译；真实 PostgreSQL 启动验证仍待 Testcontainers 集成测试覆盖。
