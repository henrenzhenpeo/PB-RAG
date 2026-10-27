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
- Tool 具备 Schema、鉴权、超时、取消、重试、审批和审计机制；
- Workspace 权限在检索和工具执行前生效；
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
- Workspace RBAC；
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

## 6. 稳定技术方向

已接受基线见 `docs/adr/0001-主服务采用-spring-boot-与-spring-ai.md`：

- Web：Vue 3、TypeScript、Vite、Vue Router、Pinia、TDesign。页面只覆盖演示主路径，见 `docs/adr/0002-前端采用精简-vue-3-控制台.md`；
- 主服务：Java 21、Spring Boot、Spring MVC、虚拟线程；
- 模型与工具基础设施：Spring AI 的 `ChatModel`、`EmbeddingModel`、`ChatClient`、`ToolCallback`、`PgVectorStore` 基线和 Micrometer Observation；
- 持久化：PostgreSQL + pgvector、Spring Data JDBC 或 `JdbcTemplate`、Flyway；
- 任务：PostgreSQL 任务表是事实来源；Redis 负责唤醒、取消标记和限流；
- 解析：首期在 Java 进程内处理 Markdown 和普通 PDF；复杂解析再按接口接入 Python；
- 安全：Spring Security；策略由确定性代码在检索和工具执行前生效；
- 部署：Docker Compose；
- 可观测：Micrometer Observation + OpenTelemetry，按需要接入 Langfuse；
- 测试：JUnit 5 + Testcontainers；
- 架构：包级模块化单体。Spring AI 提供模型和工具基础设施；Agent Runtime、Production RAG、权限、任务状态、引用和评测由本项目实现。

尚未形成 ADR 的选择都可以通过设计和实验调整，不视为不可改变的承诺。脚手架已锁定 Maven、Java 21、包名 `dev.pbrag`、Spring Boot 4.1.2 和 Spring AI 2.0.1。PostgreSQL 与 Redis 的连接地址写在 `backend/src/main/resources/application.yml`，由外部服务器提供。

## 7. 项目质量原则

- 减少功能宽度，不牺牲工程深度；
- 先做纵向闭环，再扩展横向能力；
- 先建立基线，再谈优化；
- 权限、安全和可观测从第一条链路开始设计；
- 正常路径和失败路径属于同一个功能；
- 所有改进结论必须可复现；
- UI 以支持调试、理解和演示为目标，不优先追求视觉丰富度。

## 8. 核心文档索引

- `docs/adr/0001-主服务采用-spring-boot-与-spring-ai.md`：已接受的主服务技术栈；
- `docs/adr/0002-前端采用精简-vue-3-控制台.md`：已接受的前端范围；
- `docs/ROADMAP_6_WEEKS.md`：当前正式执行计划；
- `docs/HANDOFF.md`：当前进度、验证、风险与下一步；
- `docs/PB-RAG企业级Agent工程实战方案.md`：完整知识地图；第 4.1 节是系统架构图，第 4.2 节是前端架构图，第 4.3 节是代码架构图；
- `docs/WEKNORA_项目拆解与Agent产品路线图.md`：WeKnora 产品、用户和架构拆解；
- `prompts/README.md`：运行时 Prompt 规范。

## 9. 文档优先级

如果不同文档存在冲突：

1. 用户在当前任务中的明确要求；
2. 已接受的 ADR；
3. 本文件中的稳定目标与原则；
4. `docs/ROADMAP_6_WEEKS.md`；
5. `docs/HANDOFF.md` 中的当前执行信息；
6. 早期研究和方案文档。
