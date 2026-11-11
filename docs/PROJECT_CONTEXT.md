# PB-RAG 项目上下文

> 本文件只保存跨任务稳定的信息。当前进度、验证和风险见 `HANDOFF.md`，阶段计划见 `ROADMAP_6_WEEKS.md`。

## 1. 项目定位与目标

PB-RAG 是一个面向团队技术知识的紧凑型企业知识 Agent 系统，也是用于形成真实 Agent 工程经验的学习项目。

项目发起人已有全栈开发经验并深度使用过 WeKnora，但缺少真实企业 Agent 项目经验。本项目不部署或包装现成 RAG 产品，而是独立实现、测试并解释以下核心机制：

- 可恢复、幂等、可观测的异步文档入库；
- 混合检索、Rerank、无证据拒答和版本化引用；
- 由本项目实现的 Agent Runtime 与明确的工具执行边界；
- 确定性的资源作用域、安全策略、审批和审计；
- 固定数据集上的质量、延迟、成本与故障回归。

WeKnora 是参考实现和对照组，不是代码模板。每个核心主题先提出 PB-RAG 设计并实现最小原型，再对照 WeKnora 的机制与取舍，最后用测试、评测或故障实验验证结论。

## 2. 六周版本成功标准

- 文档入库支持阶段状态、幂等、有限重试、租约恢复和明确失败信息；
- RAG 支持向量与关键词混合检索、RRF、可选 Rerank、可追溯引用和无证据拒答；
- Agent Runtime 核心循环由本仓库实现，可回放、取消并进入明确终态；
- Tool 支持 Schema 校验、确定性策略、超时、取消、重试、审批、Trace 和审计；
- Workspace 资源归属贯穿 HTTP、服务、数据库、检索、引用和 Trace；
- RAG 与 Agent 的改动可在固定、版本化数据集上回归；
- 规定的可靠性与安全故障可以复现，并有修复与回归测试。

详细里程碑与验收以 `ROADMAP_6_WEEKS.md` 为准。

## 3. 首期范围

### 必须完成

- Markdown 和普通文本 PDF；
- 文档版本、Chunk、异步入库 Job 与稳定来源位置；
- PostgreSQL、pgvector、ParadeDB BM25 和 Redis；
- 混合检索、RRF、Rerank、SSE 问答与版本化引用；
- 自研 Agent Runtime 和 3～5 个只读工具；
- 一个高风险工具审批样例；
- Trace、审计、固定评测集与故障实验；
- 支持演示主路径的 Vue 前端。

### 暂不完成

- 多租户、用户成员关系与 Workspace RBAC；
- 自动 Wiki、GraphRAG、多 Agent 协作和完整长期记忆；
- 大量模型、向量库、解析器、渠道或数据源集成；
- Kubernetes、完整技能市场、多种远程沙箱和商业计费。

## 4. 稳定技术方向

### 应用与数据

- Web：Vue 3、TypeScript、Vite、Vue Router、Pinia、TDesign；
- 主服务：Java 21、Spring Boot 4.1.2、Spring MVC、虚拟线程；
- 模型基础设施：Spring AI 2.0.1 的 Chat/Embedding/Tool 与观测能力；
- 持久化：PostgreSQL、pgvector、ParadeDB、MyBatis-Plus、Mapper XML、Flyway；
- 任务：PostgreSQL Job 表是事实来源；Redis 只负责唤醒、取消标记和限流；
- 解析：首期在 Java 进程内解析 Markdown 和普通 PDF，复杂版面、OCR、表格或 Office 文档再通过接口接入外部解析器；
- 可观测：结构化日志、Micrometer Observation 与 OpenTelemetry；
- 架构：包级模块化单体。

接受的技术边界见 ADR 0001、0003、0007 和 0008。没有实测需求和 ADR，不新增数据库、框架、服务或抽象。

### Spring AI 与项目自研边界

Spring AI 负责模型协议、Embedding、工具 Schema 和观测基础设施。Agent Runtime、Production RAG、入库 Job 状态、文档版本与引用、评测数据和运行记录由 PB-RAG 实现。

### 当前模型基线

首期使用阿里云百炼华北 2：

- Chat/RAG 与 Agent 初始模型：`qwen3.7-flash`；
- Agent 质量备选：`qwen3.7-plus` 或 `deepseek-flash`；
- Embedding：`qwen3.7-text-embedding-flash`，当前索引维度 1024；
- Rerank：`qwen3.7-text-rerank`，由独立适配器封装。

这是可通过固定评测集调整的基线，不是长期供应商锁定。评测和 Trace 应记录供应商、地域、实际模型版本、参数与代码版本。

## 5. 核心领域与数据边界

主要模块为 `identity`、`knowledge`、`ingestion`、`retrieval`、`chat`、`agent`、`tool`、`evaluation`、`observability` 和 `platform`。

```text
Controller
  → Service 接口
  → ServiceImpl（业务规则与事务）
  → Mapper / Mapper XML
  → PostgreSQL
```

Controller 不直连 Mapper；业务代码不直连 JDBC；Flyway 是唯一数据库结构管理方式。具体约束见 ADR 0003 与架构测试。

```text
Workspace
  └─ Knowledge Base
       ├─ Document → Document Version → Chunk / Ingestion Job
       └─ Chat Session → Chat Message → Message Citation
```

- 业务 ID 使用应用生成的 UUID；
- 文档、Chunk、Prompt、Agent、工具和评测数据在影响复现时必须版本化；
- 引用绑定文档版本、Chunk 与稳定来源位置；
- 资源查询同时约束 Workspace 和下级资源，复合外键防止错误关联；
- 只读取文档当前版本参与生产检索，历史运行保留其当时的版本引用。

## 6. Workspace、身份与安全边界

系统支持邮箱注册、登录、退出和个人资料，但所有已登录用户当前共享全部 Workspace。Workspace 是知识资源分组，不是租户或授权边界；不能把资源路径约束宣传为用户隔离。具体决定见 ADR 0004 和 0005。

未来引入授权时，必须在检索和工具执行之前由确定性代码校验，并覆盖 HTTP、服务、数据库、检索、工具、Trace 和审计层。模型或 Prompt 不能承担权限隐藏职责。

文档、网页、记忆、用户输入和工具输出均视为不可信数据。工具白名单、参数 Schema、审批、SSRF 防护、敏感信息处理和 Prompt Injection 防线必须由代码执行。

## 7. 关键运行时约束

### 入库

```text
PENDING → PARSING → CHUNKING → EMBEDDING → INDEXING → COMPLETED
              └──────────────→ RETRY_WAIT ───────────→ PENDING
PENDING / 处理中 / RETRY_WAIT → FAILED 或 CANCELLED
```

状态迁移由领域代码校验，数据库条件更新以期望阶段和租约所有者保证原子性。重复上传、重复消费、Worker 崩溃和模型限流必须具有明确且可测试的语义。

### 检索与引用

Production RAG 使用 pgvector 与 ParadeDB BM25 双路召回、固定 RRF 和可选 Rerank。向量、关键词、融合、重排与最终上下文阶段需要可追溯。RRF 只组合排名，不直接相加异构原始分数；动态权重必须先由固定评测集证明收益。

无足够证据时由确定性代码拒答。SSE 使用具名事件传递状态、引用、正文增量、完成或错误；前端不能从自然语言反向猜测引用。

### 时间、日志与敏感数据

- PostgreSQL 使用 `TIMESTAMPTZ` 保存绝对时间；
- API 使用带时区的 ISO 8601 字符串；前端按 `Asia/Shanghai` 展示；
- 请求使用 `X-Request-Id`，日志关联 Request、Trace、Span 和 Workspace；
- 普通日志和 Trace 不记录正文、完整文件内容、认证头、Cookie、密钥、密码或未脱敏工具参数；
- SQL 参数日志只允许在受控本地环境开启。

## 8. 质量与文档原则

- 优先完成纵向闭环，再扩展功能宽度；
- 正常路径和失败路径属于同一个功能；
- 每项改进必须有重复测试、固定评测或故障实验支持；
- 数据一致性、安全策略、可观测与恢复不能为了演示功能而省略；
- 核心流程注释解释设计原因、事务边界和失败语义，避免逐行翻译代码；
- ADR 记录有后果的决策；`HANDOFF.md` 只保留当前状态，不保存修复流水；
- 注释、编译和文档同步规则见 `RULES.md`，文档分类见 `docs/README.md`，Prompt 细则见 `prompts/README.md`。

## 9. 文档优先级

1. 当前任务中用户的明确要求；
2. 已接受且未被替代的 ADR；
3. 本文件的稳定目标与原则；
4. `ROADMAP_6_WEEKS.md`；
5. `HANDOFF.md` 的当前执行信息；
6. 代码和测试表达的当前实现事实。

如果新指令改变稳定决策，应同步更新本文件和相应 ADR，避免后续任务继承过时结论。
