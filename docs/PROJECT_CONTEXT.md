# PB-RAG 项目上下文

> 本文件保存跨对话必须延续的稳定信息。新任务先阅读本文件，再阅读六周路线和当前交接记录。

## 1. 发起人的背景与目标

项目发起人是一名已有全栈开发经验、深度使用过 WeKnora 的开发者，目前缺少真实企业环境中的 Agent 开发经历。

目标不是学习如何部署或调用现成 RAG 产品，而是：

- 通过 WeKnora 理解成熟知识 Agent 的设计；
- 独立实现一套精简但完整的企业级核心；
- 经历异步任务、检索质量、Agent Runtime、权限、安全、故障和评测等真实工程问题；
- 用代码、测试、评测数据和设计文档形成可验证的 Agent 工程经验；
- 最终形成能够用于作品展示、简历和面试讨论的项目成果。

## 2. 项目定位

PB-RAG 是一个面向团队技术知识的企业级 Agent 核心系统。

首期用户可以：

1. 创建 Workspace 和知识库；
2. 导入技术资料并查看异步处理状态；
3. 进行带引用的快速 RAG 问答；
4. 使用 Agent 完成跨文档、多步骤分析；
5. 查看检索过程、工具调用、审批、成本和执行轨迹；
6. 通过评测与 Trace 定位失败原因。

## 3. 成功标准

六周核心版本至少证明：

- 文档入库可异步执行、重试、恢复、观测并保持幂等；
- RAG 支持向量和关键词混合检索、Rerank、可追溯引用和无证据拒答；
- Agent Runtime 的核心循环由本项目实现；
- Tool 具备 Schema、策略校验、超时、取消、重试、审批和审计机制；
- Workspace 作为知识资源分组，在入库、检索、引用和 Trace 中保持一致；
- 关键链路可以用 Trace、指标和中间结果定位问题；
- RAG 与 Agent 的改动能够通过固定数据集回归；
- 项目通过规定的企业故障和安全实验。

## 4. 核心学习策略

每个主题采用以下循环：

```text
定义真实问题
  → 不看答案先设计 PB-RAG 方案
  → 实现最小原型
  → 阅读 WeKnora 对应源码
  → 分析约束与取舍
  → 修正实现
  → 测试和故障注入
  → 记录 ADR 与实验结果
```

WeKnora 是对照组，不是最终交付物。学习的是可迁移机制，而不是目录和函数名。

## 5. 首期范围

### 必须完成

- Markdown、PDF 等少量核心格式；
- 文档版本、异步入库和阶段状态；
- PostgreSQL、pgvector、Redis；
- 向量 + 关键词混合检索和 Rerank；
- SSE 流式问答与引用；
- 自主 Agent Runtime；
- 3～5 个只读工具；
- 单用户模式下的 Workspace 知识分组；
- 一个高风险工具审批样例；
- Trace、审计、评测和故障实验。

### 暂不完成

- 自动 Wiki；
- GraphRAG；
- 多 Agent 协作；
- 完整长期记忆；
- 大量模型、向量库和文档解析器；
- 大量 IM 渠道和数据源；
- Kubernetes；
- 完整技能市场；
- 多种远程沙箱后端；
- 商业计费系统。
- 多租户、用户成员关系与 Workspace RBAC；

## 6. 稳定技术方向

已接受基线见 `docs/adr/0001-主服务采用-spring-boot-与-spring-ai.md`：

- Web：Vue 3、TypeScript、Vite、Vue Router、Pinia、TDesign。页面只覆盖知识库、对话、Agent 运行和评测等演示主路径；
- 主服务：Java 21、Spring Boot、Spring MVC、虚拟线程；
- 模型与工具基础设施：Spring AI 的 `ChatModel`、`EmbeddingModel`、`ChatClient`、`ToolCallback`、`PgVectorStore` 基线和 Micrometer Observation；
- 持久化：PostgreSQL + pgvector、MyBatis-Plus、Flyway；业务模块采用 Controller、Service、ServiceImpl、Mapper、Mapper XML 分层，具体边界见 ADR 0003；
- 任务：PostgreSQL 任务表是事实来源；Redis 负责唤醒、取消标记和限流；
- 解析：首期在 Java 进程内处理 Markdown 和普通 PDF；复杂解析再按接口接入 Python；
- 安全：首期不实现登录、多租户和 RBAC；工具策略、审批、SSRF 与 Prompt Injection 防护仍由确定性代码执行；
- 部署：Docker Compose；
- 可观测：Micrometer Observation + OpenTelemetry，按需要接入 Langfuse；
- 测试：JUnit 5 + Testcontainers；
- 架构：包级模块化单体。Spring AI 提供模型和工具基础设施；Agent Runtime、Production RAG、任务状态、引用和评测由本项目实现。

尚未形成 ADR 的选择都可以通过设计和实验调整，不视为不可改变的承诺。脚手架已锁定 Maven、Java 21、包名 `dev.pbrag`、Spring Boot 4.1.2 和 Spring AI 2.0.1。PostgreSQL 与 Redis 的连接地址写在 `backend/src/main/resources/application.yml`，由外部服务器提供。

## 7. 当前架构与第一阶段设计

### 7.1 模块与调用分层

后端采用包级模块化单体，业务模块按以下结构组织：

```text
Controller
  → Service 接口
  → ServiceImpl（业务规则、事务）
  → Mapper（BaseMapper + 自定义方法）
  → Mapper.xml（定制与复杂 SQL）
  → PostgreSQL
```

主要模块为 `identity`、`knowledge`、`ingestion`、`retrieval`、`chat`、`agent`、`tool`、`evaluation`、`observability` 和 `platform`。Controller 不直接调用 Mapper；Flyway 是唯一数据库结构管理方式。

### 7.2 第一阶段数据模型

```text
workspaces
  └─ knowledge_bases
       └─ documents
            └─ document_versions
                 ├─ chunks
                 └─ ingestion_jobs
```

- Workspace 只是知识资源分组，不是租户或授权边界；
- 业务 ID 使用 UUID，由应用生成；
- 文档版本、Chunk 和引用绑定稳定的版本及来源位置；
- `workspace_id` 与复合外键用于资源归属和防止错误关联；
- `chunks.embedding` 使用 `vector(1536)`；
- PostgreSQL Job 表是任务事实来源，Redis 只负责唤醒、取消标记和限流。

### 7.3 入库状态机

```text
PENDING → PARSING → CHUNKING → EMBEDDING → INDEXING → COMPLETED
              └──────────────→ RETRY_WAIT ───────────→ PENDING
PENDING/处理中/RETRY_WAIT → FAILED 或 CANCELLED
```

状态转换由领域代码控制，禁止跳过处理阶段；任务必须支持幂等、明确失败原因、有限重试和崩溃恢复。

### 7.4 当前 API

```text
POST /api/workspaces
GET  /api/workspaces
POST /api/workspaces/{workspaceId}/knowledge-bases
GET  /api/workspaces/{workspaceId}/knowledge-bases
GET  /api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}
```

API 统一返回 `ApiResponse<T>`，同时保留正确 HTTP 状态码。下一条纵向链路是 Markdown 上传，在同一事务中创建 Document、DocumentVersion 和 IngestionJob。

## 8. 项目质量原则

- 减少功能宽度，不牺牲工程深度；
- 先做纵向闭环，再扩展横向能力；
- 先建立基线，再谈优化；
- 数据一致性、安全策略和可观测从第一条链路开始设计；
- 正常路径和失败路径属于同一个功能；
- 所有改进结论必须可复现；
- UI 以支持调试、理解和演示为目标，不优先追求视觉丰富度。

## 9. 文档结构

- `PROJECT_CONTEXT.md`：稳定目标、范围、技术栈和当前架构，新任务首先阅读；
- `ROADMAP_6_WEEKS.md`：六周里程碑与验收；
- `HANDOFF.md`：当前进度、风险和下一步，只保留现状；
- `adr/0001-主服务采用-spring-boot-与-spring-ai.md`：主服务与 Spring AI 边界；
- `adr/0003-业务持久层采用-mybatis-plus.md`：Spring/MyBatis 分层及 SQL 边界；
- `adr/0004-首期暂缓多租户与-rbac.md`：单用户范围与 Workspace 语义。

运行时 Prompt 规范独立保存在仓库根目录的 `prompts/README.md`。

## 10. 文档优先级

如果不同文档存在冲突：

1. 用户在当前任务中的明确要求；
2. 已接受的 ADR；
3. 本文件中的稳定目标与原则；
4. `docs/ROADMAP_6_WEEKS.md`；
5. `docs/HANDOFF.md` 中的当前执行信息；
6. 代码与测试所表达的当前实现事实。
