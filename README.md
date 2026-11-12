# PB-RAG

PB-RAG 是一个面向团队技术知识的紧凑型企业知识 Agent 项目。它以六周为周期，重点实现并验证文档异步入库、混合检索、可追溯引用、自研 Agent Runtime、工具治理、可观测性与评测闭环。

项目不是对 WeKnora 的复制。WeKnora 只作为机制对照；核心运行时、Production RAG、任务状态、引用和评测均在本仓库中实现。

## 当前进度

- 第一周纵向 RAG 闭环已完成：Markdown/PDF 入库、分块、Embedding、向量检索、SSE 回答与版本化引用；
- 第二周 Production RAG 主链路已完成：ParadeDB BM25、pgvector、RRF、可选 Rerank、阶段快照与前端检索调试页；
- 首版 `retrieval-v1` 评测集已有 40 条真值草案，下一步是人工复核并运行统一基线；
- Agent Runtime、工具治理、审批和完整故障实验尚未开始。

最新的完成项、风险和下一步以 [当前交接](docs/HANDOFF.md) 为准。

## 技术栈

- 后端：Java 21、Spring Boot 4.1.2、Spring MVC、Spring AI 2.0.1、MyBatis-Plus、Flyway；
- 前端：Vue 3、TypeScript、Vite、Pinia、TDesign；
- 数据：PostgreSQL、pgvector、ParadeDB BM25、Redis；
- 模型：阿里云百炼 OpenAI-compatible Chat/Embedding，以及独立 Rerank 适配器；
- 测试与观测：JUnit 5、Micrometer Observation、OpenTelemetry。

## 仓库结构

```text
backend/       Spring Boot 主服务
frontend/      Vue 单页应用
docs/          项目上下文、路线、交接与 ADR
evaluation/    版本化评测语料、数据集与后续报告
prompts/       版本化运行时 Prompt
```

注释、编译和文档同步约定见 [协作规则](docs/RULES.md)，文档职责与阅读顺序见 [文档索引](docs/README.md)。

## 本地运行

前置条件：JDK 21、Node.js 22、PostgreSQL（含 pgvector 与 ParadeDB `pg_search`）和 Redis。连接及模型配置位于 `backend/src/main/resources/application.yml`；生产或共享环境应使用外部配置覆盖凭据。

数据库与模型凭据只从环境变量读取，禁止写入仓库。Redis 密码可选：宝塔 `requirepass` 为空时不设置该变量；启用 Redis 入库即时信号前需确保应用可以连接 Redis：

```powershell
$env:PBRAG_DB_PASSWORD='...'
$env:OPENAI_API_KEY='...'
$env:PBRAG_INGESTION_REDIS_SIGNALS_ENABLED='true'
```

宝塔 Redis 默认仅绑定服务器 `127.0.0.1`。后端在开发机运行时，先建立 SSH 隧道：

```powershell
ssh -N -L 6379:127.0.0.1:6379 <服务器用户>@116.62.53.31
```

此时应用保持默认 `PBRAG_REDIS_HOST=127.0.0.1`。如果后端部署在宝塔同一服务器，也同样连接 `127.0.0.1:6379`。Redis 信号默认关闭；未启用或连接失败时，入库任务仍由 PostgreSQL 定时轮询领取。

启动后端：

```bash
cd backend
./mvnw spring-boot:run
```

Windows PowerShell 可使用：

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

默认后端端口为 `8088`，健康检查地址为 `http://localhost:8088/actuator/health`。

真实百炼 Rerank 默认关闭。需要供应商业务空间 URL 与 API Key 时，通过环境变量显式启用：

```powershell
$env:PBRAG_RERANK_ENABLED='true'
$env:PBRAG_RERANK_URL='https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank'
$env:PBRAG_RERANK_API_KEY='...'
```

启动后在知识库详情页进入“检索调试”，确认 `RERANK` 阶段为 `COMPLETED`，并保存运行 ID、模型、配置、阶段延迟和固定数据集结果。未提供这些凭据时系统会明确显示 `RERANK_DISABLED`，不能据此宣称真实 Rerank 已验证。

启动前端：

```bash
cd frontend
npm install
npm run dev
```

开发代理会把 `/api` 转发到后端。常用验证命令：

```bash
cd backend && ./mvnw test
cd frontend && npm run type-check
cd frontend && npm run lint
cd frontend && npm run build-only
```

## 项目边界

当前 Workspace 是已登录用户共享的知识资源分组，不是租户或授权边界。多租户、成员关系和 RBAC 尚未实现；对外部署前必须补齐注册限制、限流和授权模型。

