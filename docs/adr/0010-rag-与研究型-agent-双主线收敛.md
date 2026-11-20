# ADR 0010：RAG 与知识调研 Agent 双主线收敛

- 状态：已接受
- 日期：2026-11-20
- 影响文档：`docs/PROJECT_CONTEXT.md`、`docs/ROADMAP_6_WEEKS.md`、`docs/HANDOFF.md`
- 关联决策：ADR 0007、0009；收紧 ADR 0009 的首期工具、Skill、审批和框架对照实施范围，不改变自研 Runtime 决定

## 背景与问题

Production RAG 已有固定检索基线，Agent 已跑通计算工具的首个同步切片。原六周计划同时要求多种工具、Skill、审批、MCP、沙箱和框架对照，难以在保留引用、评测与故障验证深度的前提下完成。项目仍需要同步积累 Agent 工程能力。

用户给出的 [Deep Research 教学参考](https://www.wushixiongai.com/projects/deep-research/c/01-why-deep-research) 展示了“规划、多步搜索、交叉验证、带引用报告”的任务形态。PB-RAG 采用其问题形态作为参考，不照搬代码或以展示隐藏思维过程作为需求。

## 决定

六周版本保留两条同步主线。RAG 负责一次检索即可支持的问答；研究型 Agent 面向同一 Workspace 与知识库，拆解跨文档问题，反复调用受治理的知识工具，核对证据、冲突与缺口，输出带文档版本和稳定位置引用的简短调研报告。用户可直接选择 RAG 问答或 Agent 调研；自动路由不是首期前置条件。

Agent 复用 Production RAG 的召回、证据门槛和引用来源。首期工具限于 `search_knowledge`、`read_document` 与已实现的 `calculator`。保留结构化 Tool Call、Run/Step、Deadline、取消、租约、审计和确定性策略。报告展示可观察的计划、工具调用、证据和结论，不要求模型输出或存储隐藏思维过程。知识工具只读，仍须处理参数校验、作用域、超时、取消和失败收敛。

Skill、高风险写工具审批、更多领域工具、MCP、LangGraph4j 对照和沙箱移至 P2。ADR 0009 对这些能力的架构边界继续有效，实施顺序以本 ADR 和路线图为准。没有外部网页搜索时，报告只对入库知识作结论，并明确时效和证据缺口。

## 验收与后果

- 同一批版本化文档上，简单问题走 RAG；跨文档问题由 Agent 多步查找并输出逐项引用的报告。
- 固定数据集包含单文档、跨文档、冲突和无证据任务；记录正确性、引用、任务成功率、延迟、Token、成本和版本。
- 回归取消、重复调用、工具超时、重启恢复、Workspace 作用域和 Prompt Injection；错误进入明确终态。
- 范围缩小降低集成数量，但首期不展示写操作审批、互联网调研或完整 Deep Research 长任务能力。
