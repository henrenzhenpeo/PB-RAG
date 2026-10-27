# PB-RAG 当前交接记录

> 本文件记录当前状态，而不是保存完整聊天历史。每次完成重要工作后更新。

## 当前阶段

Spring Boot 与 Vue 3 骨架已生成。PostgreSQL 和 Redis 使用 `application.yml` 中的地址，尚未连接服务器。

## 已完成

- 完成 WeKnora 产品、用户、架构和源码学习方向梳理；
- 完成 PB-RAG 企业级 Agent 工程实战总方案；
- 将执行周期从十二周压缩为六周；
- 建立仓库级协作规则、稳定项目上下文和跨对话交接机制；
- 建立 Prompt 目录规范与两份初始基线 Prompt；
- 接受 ADR 0001：主服务改为 Spring Boot 与 Spring AI，并同步路线和工程方案；
- 用 Spring Initializr 生成 `backend/`：Spring Boot 4.1.2、Spring AI 2.0.1、Java 21、包名 `dev.pbrag`；
- 用 create-vue 生成 `frontend/`：Vue 3、TypeScript、Vite、Vue Router、Pinia、TDesign。
- PostgreSQL 与 Redis 只保留连接地址，不在本机安装服务；
- 接受 ADR 0002：前端使用精简 Vue 3 控制台，并写入第 4.2 节架构图。

## 当前有效文档

- `AGENTS.md`
- `docs/PROJECT_CONTEXT.md`
- `docs/ROADMAP_6_WEEKS.md`
- `docs/PB-RAG企业级Agent工程实战方案.md`
- `docs/WEKNORA_项目拆解与Agent产品路线图.md`
- `prompts/README.md`
- `docs/adr/0001-主服务采用-spring-boot-与-spring-ai.md`
- `docs/adr/0002-前端采用精简-vue-3-控制台.md`
- `backend/`
- `frontend/`

## 已确认决策

- 项目目标是获得企业级 Agent 核心开发经验，不追求 WeKnora 功能平替；
- 采用六周执行周期；
- 减少功能宽度，但保留 Agent Runtime、权限、评测、可观测与失败处理；
- WeKnora 作为参考实现，核心机制需经过独立设计和实现；
- 新任务通过仓库文档恢复上下文，不依赖聊天记忆；
- 主服务采用 Java 21、Spring Boot、Spring MVC 和虚拟线程；
- Spring AI 负责模型、Embedding、工具 Schema、第一周向量基线和观测；
- Agent Runtime、Production RAG、权限、任务状态、引用和评测由本项目实现；
- 主持久化使用 JDBC 与 Flyway；PostgreSQL 任务表是任务事实来源，Redis 负责唤醒、取消标记和限流；
- 首期在 Java 进程内解析 Markdown 和普通 PDF，复杂解析再接入 Python；
- 构建工具为 Maven，Java 21，包名 `dev.pbrag`，Spring Boot 4.1.2，Spring AI 2.0.1；
- PostgreSQL 和 Redis 由外部服务器提供，地址维护在 `backend/src/main/resources/application.yml`；
- 前端采用 Vue 3、TypeScript、Vite、Vue Router、Pinia 和 TDesign，页面只覆盖知识库、对话、Agent 运行和评测。

## 尚待确认

- 每周实际可投入时间；
- `application.yml` 中的 PostgreSQL 和 Redis 服务器地址、账号；
- PostgreSQL 关键词检索采用原生 FTS 还是 ParadeDB；
- 首个远程模型、Embedding 和 Rerank 供应商；
- 是否已有可作为首批数据集的技术文档。

## 当前风险

- 六周范围紧，需要严格限制解析格式、外部集成和 UI 工作量；
- 尚未建立 WeKnora 对照基线数据；
- 尚未建立真实评测问题集；
- 应用要等 `application.yml` 指向可连接的 PostgreSQL 和 Redis 后才能完成启动验证；
- 实现时若把 Agent 循环或 Production RAG 交给 Spring AI 默认 Advisor，ADR 0001 的学习目标会失效。第 3 周和第 5 周要用测试与同数据集对比守住这条边界。

## 下一步推荐动作

编写《第一阶段技术设计》，确认首个纵向切片的：

1. 把 `application.yml` 里的数据库和 Redis 地址改成服务器地址并启动；
2. 模块边界；
3. 最小数据模型；
4. 文档入库状态机；
5. API 和 SSE 事件；
6. 两周任务拆分；
7. 第一批测试与验收标准。

## 最近验证

- 2026-10-24：确认所有上下文、路线、交接和 Prompt 文件存在；
- 2026-10-24：执行 `git diff --check`，未发现空白或补丁格式问题；
- 2026-10-24：检查 `AGENTS.md` 的启动顺序与文档交叉引用，路径一致；
- 2026-10-27：主服务决策改为 Spring Boot 与 Spring AI，已写入 ADR 0001，并同步 `AGENTS.md`、项目上下文、六周路线、工程方案和 WeKnora 拆解中的 PB-RAG 设计建议；
- 2026-10-27：生成 `backend/` 官方骨架。PostgreSQL 与 Redis 只在 `application.yml` 保留地址，未在本机安装。上下文测试在地址可连接前保持禁用；
- 2026-10-27：当前系统架构图写入 `docs/PB-RAG企业级Agent工程实战方案.md` 第 4.1 节；
- 2026-10-27：接受 ADR 0002，前端架构图写入同一方案第 4.2 节；
- 2026-10-27：代码架构图写入同一方案第 4.3 节；
- 2026-10-27：用 create-vue 生成 `frontend/`，接入 TDesign、路由、Pinia、axios 和 SSE。需要 Node.js 22。

## 更新模板

后续更新本文件时保留以下结构：

```text
当前阶段
已完成
当前有效文档
已确认决策
尚待确认
当前风险
下一步推荐动作
最近验证
```
