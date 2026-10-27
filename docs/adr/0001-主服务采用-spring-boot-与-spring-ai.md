# ADR 0001：主服务采用 Spring Boot 与 Spring AI

- 状态：已接受
- 日期：2026-10-27
- 影响文档：`AGENTS.md`、`docs/PROJECT_CONTEXT.md`、`docs/ROADMAP_6_WEEKS.md`、`docs/HANDOFF.md`、`docs/PB-RAG企业级Agent工程实战方案.md`、`docs/WEKNORA_项目拆解与Agent产品路线图.md`

## 背景

PB-RAG 要获得的是企业级知识 Agent 的核心工程经验：异步入库、混合检索、引用、Agent Runtime、权限、故障处理和评测。WeKnora 是 Go 实现的参考系统，用来对照机制，不是必须逐目录移植的代码基线。

原建议技术栈是 Go 模块化单体，外加自研模型适配层和 Asynq。项目发起人的主要技术背景和求职方向是 Java 企业开发。文档规划已经完成，代码实现尚未开始。

## 问题

主服务应继续使用 Go，以便与 WeKnora 保持同语言对照，还是改为 Spring Boot 与 Spring AI，把六周时间集中在 Agent、RAG、权限、可靠性和评测上？

## 约束

- 执行周期仍为六周，建议每周投入 20～30 小时。
- Agent 核心循环必须由本仓库实现，并保持可测试、可解释。
- 授权必须发生在检索和工具执行之前，由确定性代码执行。
- 异步任务必须幂等，并暴露阶段、重试、失败和恢复信息。
- 换技术栈不能增加功能宽度，也不能拿掉评测、权限、Trace、幂等和失败处理。
- 具体框架版本在脚手架初始化时锁定为 GA 版本，不跟踪 snapshot。

## 候选方案

1. 维持 Go、Gin/Chi、自研模型适配层和 Asynq。对照 WeKnora 最直接，但六周里会有一部分时间花在 Go 工程生态上。
2. 以 Spring AI 的 Advisor、默认 Tool Loop、JPA 和响应式栈作为系统本体。样板代码最少，但 Agent Runtime、任务恢复、权限和生产检索会藏进框架，学习目标会被削弱。
3. 使用 Spring Boot 作为模块化单体，Spring AI 只承担模型、Embedding、工具 Schema、向量基线与观测；Agent Runtime、Production RAG、治理、任务状态、引用和评测仍由本项目实现。

## 决定

采用方案 3，并锁定以下边界。

### 应用与持久化

- 语言与运行时：Java 21+、Spring Boot、Spring MVC、虚拟线程。
- 持久化：Spring Data JDBC 或 `JdbcTemplate`，迁移使用 Flyway。主持久化不使用 JPA。
- 模块：按包划分 `identity`、`knowledge`、`ingestion`、`retrieval`、`chat`、`agent`、`tool`、`evaluation`、`observability`、`platform`。首期不引入 Spring Modulith。
- 安全：Spring Security 与方法级鉴权。Workspace 作用域贯穿 HTTP、服务、仓储、检索、工具、Trace 和审计。

### Spring AI 的职责

直接复用：

- `ChatModel`：统一模型调用；
- `EmbeddingModel`：Embedding；
- `ChatClient`：仅用于普通问答和流式输出；
- `PgVectorStore`：第一周的基础向量读写、相似度阈值和元数据过滤；
- `ToolCallback`：工具描述和参数 Schema；
- Micrometer Observation：模型和工具观测，并导出 OpenTelemetry；
- MCP Client/Server：后期实验；
- Evaluator：评测运行器中的一项能力，不替代自研评测集和运行记录；
- ETL 的 Reader、Transformer、Writer：只借鉴简单 ETL 的接口形状。

`QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor` 和 `ToolCallingAdvisor` 保留为框架基线，供第 5 周同数据集对比。它们不是 Production RAG 或 Agent Runtime 的实现。

### 本项目继续实现

Agent Runtime 提供 `start`、`resume` 和 `cancel`。内部包含 Run/Step 状态机、最大迭代、重复工具调用检测、单步和总任务超时、用户取消、工具错误分类与重试、人工审批暂停与恢复、上下文预算、Run/Step 持久化、SSE 运行事件，以及服务重启后的恢复策略。从第 3 周起，循环直接使用 `ChatModel` 和显式 Tool Registry。

Production RAG 自行编排并保存中间状态：权限过滤、Query Rewrite、向量检索、PostgreSQL FTS/BM25、RRF、Rerank、邻接扩展、去重、上下文组装和引用生成。权限条件使用绑定参数写入 SQL。Spring AI 的 metadata filter 可以辅助第一周基线，但不能代替 Workspace 隔离。

`ChatMemory` 只提供模型当前回合需要的上下文。完整会话、Agent Step 和审计分别落在 `sessions`、`messages`、`agent_runs`、`agent_steps`、`tool_executions` 和 `approval_requests`。

工具按 Agent 白名单注册。禁止把带工具注解的 Spring Bean 全部暴露给模型。

### 任务与解析

- 入库和 Agent 任务的事实来源是 PostgreSQL 任务表，领取使用 `SKIP LOCKED`。任务记录阶段、重试次数和失败原因。
- Redis 负责唤醒、取消标记和限流。它不单独承担可恢复任务状态。
- 第一周在 Java 进程内解析 Markdown 和普通 PDF。
- 当 OCR、版面、表格或复杂 Office 文档挡住评测时，再按 `Parser` 接口接入独立 Python 服务。

### 测试

使用 JUnit 5 与 Testcontainers。核心链路需要覆盖重复请求、并发、超时、取消、重试、重启、授权隔离和敏感数据处理。

## 理由

Spring AI 已经提供模型调用、Embedding、PGvector、工具 Schema、MCP、观测和基础评测接口，适合去掉接入样板。Java 21 虚拟线程足以支撑阻塞式的模型调用、SSE、超时和取消，同时让 JDBC 与任务状态机保持直观。

手写 SQL 更适合混合检索、文档版本、Chunk 位置和 Workspace 过滤。任务状态与业务数据放在同一数据库，崩溃恢复和 Testcontainers 测试都更直接。

由此形成的项目故事是：拆解 Go 实现的 WeKnora，抽取企业级 Agent 机制，再在 Spring AI 上独立实现，并用同一数据集比较框架默认 RAG/Tool Loop 与自研 Runtime。

## 负面影响

- 对照 WeKnora 时只能比较机制，不能按 Go 目录逐文件迁移。
- Spring AI 的 Advisor、VectorStore 表结构和过滤器表达式会变化，必须把 GA 版本写入构建文件。
- `PgVectorStore` 的默认表不能成为文档版本、Chunk 和引用位置的领域模型。第一周之后，检索仓储以自研 SQL 为主。
- 虚拟线程不替代连接池上限、外部 HTTP 超时和取消传播设计。
- 如果实现滑向 `ChatClient` 的默认工具循环或单个 RAG Advisor，本 ADR 的学习目标就失效了。

## 脚手架锁定

- 构建：Maven，工程位于 `backend/`，包名 `dev.pbrag`。
- 版本：Java 21、Spring Boot 4.1.2、Spring AI 2.0.1。
- 官方 starter：WebMVC、Spring Data JDBC、Flyway、Redis、Actuator、Validation、OpenAI、PgVector、Markdown 与 PDF Document Reader。
- PostgreSQL 和 Redis 不在本机安装。连接地址写在 `backend/src/main/resources/application.yml`，启动前改成目标服务器。
- 未配置模型密钥时不装配 ChatModel 和 EmbeddingModel。`openai` profile 打开官方模型与 PgVectorStore。
- 远程观测和线上部署不在这次脚手架范围内。

## 后续验证

- 第 1 周纵向切片使用 MVC、JDBC、Flyway、PostgreSQL 任务表和 Spring AI 的 Chat/Embedding 基线完成。
- 第 3 周用测试覆盖取消、超时、重复工具调用和重启恢复，并证明循环由本项目的 Runtime 推进。
- 第 4 周用跨 Workspace 测试证明未授权内容不会进入检索结果和工具执行。
- 第 5 周在同一数据集上比较自研流水线与 Spring AI 默认 Advisor：Recall@K、引用准确率、延迟、Token、可观测性和权限控制。
- 每次评测记录数据集、文档、Prompt、模型、配置和代码版本。
