# ADR 0009：Agent Runtime 采用领域编排并以 LangGraph4j 作为对照

- 状态：已接受
- 日期：2026-11-18
- 影响文档：`docs/PROJECT_CONTEXT.md`、`docs/ROADMAP_6_WEEKS.md`、`docs/HANDOFF.md`、`prompts/agent/knowledge_agent.md`
- 关联决策：ADR 0001

## 背景

PB-RAG 已完成第一周纵向 RAG 闭环和第二周 Production RAG 主链路，即将进入 Agent Runtime。项目需要同时回答两个问题：一是如何把知识检索升级为能够多步完成业务目标的 Agent；二是是否应直接采用 LangGraph 或 LangGraph4j，避免重复实现运行时编排。

当前主服务采用 Java 21、Spring Boot 和 Spring AI。ADR 0001 已确定 Spring AI 负责模型、工具 Schema、MCP 和观测基础设施，Agent Runtime、运行状态、工具治理、审批、恢复与评测由 PB-RAG 实现。项目同时具有学习目标：关键运行机制必须能够解释、测试和复现，不能只由第三方框架隐藏。

## 问题

PB-RAG 应直接以 LangGraph/LangGraph4j 作为生产 Agent Runtime，还是自行实现领域运行时？ReAct、MCP 与 Skill 应如何进入同一架构而不形成彼此重叠的框架？

## 约束

- 主服务保持 Java 模块化单体；没有实测需求和 ADR，不增加 Python Agent 服务；
- PostgreSQL 继续保存可恢复任务和业务状态的事实记录；
- Workspace 资源作用域、工具策略、审批和审计必须由确定性代码执行；
- Agent 核心循环需要可测试、可回放、可取消并进入明确终态；
- 外部工具调用必须处理 Schema、超时、取消、重试、幂等、脱敏和结构化错误；
- 首期优先完成单 Agent 纵向闭环，不以多 Agent、技能市场或任意代码执行扩大范围；
- 框架收益必须通过同一数据集和工具契约的可重复实验验证。

## 候选方案

### 方案一：使用官方 Python LangGraph 独立服务

由 Python LangGraph 承担图编排、Checkpoint、Interrupt 和恢复，Java 主服务通过 HTTP 或 MCP 与其交互。

优点是官方生态、资料和高级编排能力较完整。缺点是引入第二语言和服务边界，增加部署、认证、Workspace 上下文、Trace、取消传播和跨服务一致性成本，不符合首期模块化单体边界。

### 方案二：以 LangGraph4j 作为生产编排核心

在 Java 主服务内使用 LangGraph4j 的 StateGraph、CheckpointSaver、Interrupt、Resume 和 Spring AI 适配。

优点是能够减少图编排、暂停恢复和流式状态处理代码。缺点是 LangGraph4j 是独立社区项目而非官方 Java 实现；其 Checkpoint 与 PB-RAG 的 `agent_runs`、`agent_steps`、`tool_executions` 容易形成双重状态来源。工具副作用的幂等、Workspace 约束、审批参数绑定和审计仍需自行实现。

### 方案三：自研紧凑的领域 Runtime，LangGraph4j 作为对照实现

PB-RAG 只实现当前知识 Agent 所需的最小 ReAct 循环与 Run/Step 状态机，不建设通用图框架。LangGraph4j 不进入首期生产依赖，在 Agent 基线稳定后使用相同 Prompt、工具、模型配置和数据集实现对照实验。

## 决定

采用方案三。

### 生产运行时边界

PB-RAG 自行实现 `start`、`resume` 和 `cancel`，并保持以下职责：

- `AgentRun` / `AgentStep` 状态机和明确终态；
- 直接使用 Spring AI `ChatModel` 的结构化工具调用实现 ReAct 循环；
- 最大迭代、总 Deadline、单步超时、重复工具调用检测和取消传播；
- Run/Step、模型调用摘要、工具执行、审批和引用的持久化与回放；
- 服务重启后的恢复、失败收敛和业务幂等；
- SSE 运行事件和前端运行轨迹。

不要求模型输出或持久化自由文本的隐藏思维过程。系统只保存完成回放和诊断所需的模型调用元数据、结构化 Tool Call、规范化参数、Observation 摘要、稳定来源引用、错误和状态迁移。

### ReAct、MCP 与 Skill 的分层

- **ReAct** 是 Runtime 内部的决策循环：模型提出结构化工具调用，系统执行确定性校验和工具调用，再把 Observation 返回模型；
- **MCP** 是远程工具协议：MCP 工具必须转换为 PB-RAG 内部 `AgentTool` 契约，并经过与本地工具相同的白名单、Schema、策略、超时、脱敏和审计；
- **Skill** 是版本化业务能力包：由任务 Prompt、工具白名单、输入输出契约、运行限制、策略和评测数据集组成，不是新的 Runtime，也不能扩大调用者权限。

第一版 Skill 随仓库发布并接受代码评审，不支持在线市场、从未知来源安装、自动安装系统依赖或任意 Shell。需要运行不可信代码时，另行通过 ADR 和实测决定 Sandbox 后端。

### 工具实施顺序

1. 本地只读工具：`search_knowledge`、`read_document`、`list_documents`、`compare_documents` 和 `calculator`；
2. PB-RAG MCP Server：以只读方式发布经过 Workspace 作用域约束的知识工具；
3. PB-RAG MCP Client：接入一个受控的只读业务工具，验证远程发现、错误转换、超时和审计；
4. 一个需要参数绑定审批的外部写操作样例；
5. 只有评测任务需要时才实验受限代码执行和 Sandbox。

### LangGraph4j 对照边界

生产领域接口不得暴露 LangGraph4j 类型。编排入口保持中立，例如 `AgentOrchestrator`，以便对照实现复用相同的 Tool Registry、Policy、Approval、模型网关和评测数据。

LangGraph4j 对照至少比较：

- 任务成功率、工具选择与参数正确率；
- 审批暂停/恢复和用户取消语义；
- Worker 崩溃、服务重启和工具副作用重复执行；
- Run/Step 轨迹完整度与失败可诊断性；
- 延迟、Token、代码量、框架侵入和升级成本。

对照结果写入版本化实验报告。在没有报告和替代 ADR 前，LangGraph4j 不成为生产运行时依赖或 Agent 状态事实来源。

## 理由

首期 Agent 是单 Agent、有限工具的知识任务，核心流程可以由紧凑状态机清楚表达，尚未出现必须使用通用图引擎的复杂分支、并行子图或多 Agent 需求。自研领域 Runtime 能使 Workspace 作用域、工具副作用、审批、取消、恢复和数据库事务保持可解释，并满足项目的学习与评测目标。

同时保留 LangGraph4j 对照可以避免把“自研”变成未经验证的偏好。如果实验表明它在可靠性、开发成本或复杂工作流上有明确收益，可新增 ADR 替代本决策的编排部分。

## 后果

### 正面影响

- Agent 业务状态继续由 PB-RAG 领域模型和 PostgreSQL 掌握；
- 不新增 Python 服务，部署和 Trace 边界保持紧凑；
- ReAct、MCP、Skill 各自职责清楚，可以独立测试和演进；
- 能用相同数据证明自研 Runtime 与框架方案的真实差异；
- 将来替换编排实现时，不需要改写工具治理和业务接口。

### 负面影响

- 首期需要自行实现循环、状态迁移、暂停恢复和 SSE 事件；
- 必须为并发、崩溃、重复执行和审批恢复编写更多测试；
- 在对照实验完成前，不能直接使用 LangGraph4j 的图编排便利能力；
- 如果后续出现大量并行、子图和复杂人工流程，紧凑 Runtime 可能需要被图引擎部分替代。

## 验证

- 单元测试覆盖状态迁移、最大迭代、重复调用、错误分类和策略拒绝；
- PostgreSQL 集成测试覆盖 Run 领取、租约恢复、取消/完成竞争、审批恢复和工具执行幂等；
- 端到端测试覆盖跨文档比较、无证据终止、用户取消、超时和一个审批工具；
- `agent-v1` 固定数据集记录 Prompt、模型、工具版本、代码版本、任务成功率、参数正确率、引用准确率、延迟和成本；
- 使用相同数据运行 PB-RAG Runtime、Spring AI 默认 Tool Loop 和 LangGraph4j 对照，发布实验报告后再评估是否替换编排层。
