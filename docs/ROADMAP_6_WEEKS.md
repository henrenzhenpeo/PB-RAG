# PB-RAG 六周执行路线

> 状态：当前正式计划
> 技术栈：`docs/adr/0001-主服务采用-spring-boot-与-spring-ai.md`
> 建议投入：每周 20～30 小时。

功能级进度、逐项证据与当前行动顺序见 [功能级甘特图与当前焦点](FEATURE_GANTT_AND_CURRENT_FOCUS.md)；该图按相对周次维护，不把计划格子视作完成。

## 执行策略

六周版本通过减少集成数量和外围功能压缩周期，但保留以下深度：

- RAG 与研究型 Agent 同步推进：前者回答可由一次检索支持的问题，后者处理需要拆解、多轮检索和跨文档核对的问题；两者复用同一知识库、检索、版本化引用和评测记录；
- Agent 的首期报告包含研究问题、分项发现、逐项来源、证据缺口与结论；计划和每次工具调用可在 Run/Step 中查看，不展示或存储模型隐藏思维过程；
- Agent Runtime 自主实现；
- Workspace 资源归属在入库、检索和引用链路中保持一致；
- 邮箱账号可登录；当前所有已登录用户共享 Workspace，成员权限另行设计；
- 异步任务具备幂等、重试和可观测状态；
- 核心链路具备 Trace；
- RAG 与 Agent 有固定评测集；
- 必须完成故障和安全实验。

### Agent 知识点如何嵌入项目

附图中的四块内容作为学习与实践地图，不代表四块都要在六周内做成生产功能。每个主题先解释问题与 PB-RAG 设计，再对照现有实现、补验证证据；框架或多 Agent 的收益需用同一任务集证明。

| 附图主题 | PB-RAG 对应实践 | 阶段与验收证据 |
| --- | --- | --- |
| 什么是 Agent：从 LLM 到 Agent、核心架构、项目背景 | 用固定 RAG 问答和知识调研任务说明模型、Runtime、工具、Observation、证据与终态各自负责什么 | W3：能从一次 Run 的 Step/Tool Execution 和引用复盘完整调用链，并解释何时选 RAG、何时选 Agent |
| 规划模块：任务规划、推理与行动、状态/数据库存储 | 有界子问题计划、结构化 ReAct Tool Call、多轮查找；PostgreSQL 保存 Run/Step/Tool Execution、版本与恢复事实 | W3～W4：跨文档报告及取消、超时、重启后的明确终态；只展示可观察的决策与执行记录 |
| 单 → 多 Agent：主流开发模式、Multi-Agent 协作、LangGraph | 先把单 Agent 知识调研作为基线；记录可并行子任务与单 Agent 瓶颈，使用相同 Prompt、工具契约和数据集做 LangGraph4j 或多 Agent 对照 | 六周后独立实验：只有质量、延迟、成本或可靠性收益明确，才考虑改变生产编排；当前不引入 Python LangGraph 服务 |
| 工程落地：前后端服务、Code Interpreter、简历表达 | 完成 API/SSE、运行轨迹页、Trace、审计、评测和故障复现；计算需求先用受限 `calculator` | W4～W6：可部署与演示、指标和故障证据支撑项目复盘；任意代码执行与沙箱留待有实际任务和独立 ADR 后评估 |

每周学习考察按 [学习考察与记录](LEARNING_ASSESSMENT.md) 选题；功能状态及证据门槛见 [功能级甘特图与当前焦点](FEATURE_GANTT_AND_CURRENT_FOCUS.md)。

### 双业务场景验收

[QMS 质量异常排查与设计协同资料核查](BUSINESS_SCENARIOS_QMS_DESIGN.md)作为同一 RAG/Agent 能力的两组演示及评测任务。先以虚构、脱敏的 QMS 文档完成跨文档调研闭环，再复用 Runtime 验证设计规范与项目资料快照的逐项核查。两组均须区分文档知识、带时间的业务快照和实时事实；首期不引入真实企业系统接入。W5 固定数据集覆盖冲突、旧版本与缺口，W6 演示两组场景各一条 RAG 问答和 Agent 调研路径。

## 第 1 周：工程骨架与最短 RAG 闭环

### 目标

跑通第一条纵向链路：

```text
上传 Markdown/PDF
  → 异步任务
  → 解析与分块
  → Embedding
  → pgvector 检索
  → SSE 回答
  → 引用原文
```

### 工作内容

- Spring Boot、Spring MVC、Java 21 虚拟线程、PostgreSQL、pgvector、Redis 和 Docker Compose；
- Flyway、MyBatis-Plus、Mapper XML、配置、结构化日志、请求 ID 和 Micrometer/OpenTelemetry Trace；
- 文档、版本、Chunk、Job、Session、Message 和 Citation 模型；
- 文件上传和对象存储接口；
- PostgreSQL 任务表、入库阶段和失败原因；Redis 负责唤醒、取消标记和限流；
- Spring AI `EmbeddingModel` 与 `PgVectorStore` 基线、基础向量检索和流式生成；
- Vue 3 知识库详情页和对话页：上传、任务阶段、流式回答和引用。

### 验收

- 一条命令启动环境；
- 上传接口快速返回 Job ID；
- 能查看处理阶段和失败原因；
- 回答引用绑定文档版本；
- 端到端链路拥有 Trace ID；
- CI 可执行测试和迁移检查。

## 第 2 周：Production RAG

### 工作内容

- 关键词检索、向量检索、RRF 和 Rerank；
- 文档内容 Hash、任务幂等、失败重试；
- 文档重解析与索引版本切换；
- 保存各检索阶段中间结果；
- 无证据拒答；
- 建立首版固定 RAG 评测集，并逐步扩充难例与引用标注；

### 验收

- 可以查看向量、关键词、融合和重排结果；
- 重复消费不会生成重复 Chunk；
- Worker 崩溃后任务可恢复；
- 文档更新不会造成引用版本混乱；
- 输出 Recall@K、引用准确率、拒答率和延迟基线。

## 第 3 周：Agent Runtime

### 工作内容

- Agent Run/Step 状态机；
- 基于 `ChatModel` 结构化 Tool Call 与显式 Tool Registry 的自研 ReAct 核心循环；不保存自由文本隐藏思维过程；
- Tool Registry 与 JSON Schema 校验；
- `search_knowledge`、`read_document` 与现有 `calculator`；检索工具复用 Production RAG 的 Workspace、知识库、当前文档版本、证据门槛和来源位置；
- 有界的研究计划与跨文档报告契约：拆解子问题，按证据缺口继续查找，核对冲突与缺失，输出逐项引用和未证实事项；
- 最大迭代、重复调用检测、Deadline、单步超时、取消和有限重试；
- SSE 执行事件、轨迹持久化和回放；
- 运行页展示 Run/Step、工具参数和取消。

### 验收

- Agent 能完成跨文档比较任务；
- 简单问题仍可走固定 RAG；
- 每轮模型调用、工具调用和 Observation 可回放；
- Prompt、工具版本和模型配置可以从 Run 记录复现；
- 报告每项事实可追到文档版本和来源位置，证据不足时明确标注；
- 用户取消会传递到模型和工具；
- 循环和超时进入明确终态；
- 错误有结构化分类。

## 第 4 周：双主线可靠性与安全

### 工作内容

- 工具白名单、参数 Schema 与确定性执行策略；
- 工具审计与敏感参数脱敏；
- Prompt Injection 基础测试与报告引用核验；
- Agent 服务重启处理策略；
- RAG 的流中断、入库恢复和 Agent 的取消、超时、租约恢复故障实验；

### 验收

- Agent 不能突破工具白名单与确定性执行策略；
- 敏感值不进入普通日志和 Trace；
- 恶意文档不能改变确定性工具策略；
- Agent 知识工具不能跨越请求指定的 Workspace 和知识库；
- 服务重启后任务能够恢复、补偿或明确失败。

## 第 5 周：评测、故障实验与优化

### 工作内容

- 完善 RAG 评测集；
- 建立首版固定 Agent 多步骤任务集，覆盖单文档、跨文档、冲突证据和证据不足；
- 统计工具选择、参数正确率、任务成功率、成本和延迟；
- 完成分块、Hybrid、Rerank、工具描述或上下文预算实验；
- 在相同知识语料上分别记录 RAG 问答与 Agent 调研的质量、延迟、Token 和成本；简单题确认 RAG 路径，多步题核对 Agent 的增益；
- 完成优先级最高的故障注入。

### 必做故障

- 重复上传与重复消费；
- Parser/Worker 中途崩溃；
- Embedding 429；
- LLM 流中断；
- 用户取消；
- Tool 超时；
- Agent 重复循环；
- 服务重启；
- Workspace 资源错误关联；
- Prompt Injection；
- 日志敏感信息泄漏。

### 验收

- 每项优化都有相同数据集上的前后对比；
- 可以把失败分类到检索、推理、工具、权限或基础设施；
- 输出任务成功率、引用准确率、P95 延迟和平均成本；
- 输出 RAG 与 Agent 各自的基线及适用问题边界；报告引用与证据缺口经人工抽样核对；
- 故障实验有复现、Trace、修复和回归测试。

## 第 6 周：作品化与复盘

### 工作内容

- 清理核心边界和关键测试；
- 完善 README、部署、架构和 API 文档；
- 整理 ADR、Runbook、评测和故障报告；
- 准备演示数据和演示脚本；
- 录制演示；
- 整理简历数据和面试问答。

### 最终演示

```text
创建 Workspace
  → 上传资料并查看异步状态
  → 展示幂等或失败恢复
  → 带引用的 RAG 问答
  → Agent 展示计划、多轮知识检索和带引用的调研报告
  → Trace 定位一次失败
  → 比较两次评测运行
  → 展示工具审计与证据缺口
```

### 验收

- 新环境可以按文档部署；
- 10～15 分钟能够演示完整核心闭环；
- 项目结论有测试和评测数据；
- 可以解释架构取舍、失败处理和安全边界；
- `docs/HANDOFF.md` 不存在未说明的关键阻塞。

## 六周版本优先级

```text
P0：入库、Production RAG、知识调研 Agent、双主线评测、数据一致性、可靠性
P1：只读工具治理、审计、Trace、故障与 Prompt Injection 实验
P2：写工具审批、业务 Skill、更多工具、MCP Client/Server、LangGraph4j 对照、上下文压缩、简单沙箱
P3：Wiki、GraphRAG、多 Agent、多渠道、完整长期记忆
```

如果进度落后，依次移除 P2、减少文档格式和简化前端；不得移除 RAG 与 Agent 共同的证据链、固定评测、数据一致性和失败处理。第 1～2 周是已实现的历史里程碑；第 3～6 周按双主线收敛后的验收执行。
