# retrieval-v1 首轮人工抽样报告

## 范围与限制

- 日期：2026-11-18；
- 数据集：`evaluation/datasets/retrieval-v1.jsonl`；
- 语料：`evaluation/corpus/retrieval-v1-manifest.json`；
- 模型：`qwen3.7-text-embedding-flash`、`qwen3.7-text-rerank`；
- 流程：Vector Top 30、ParadeDB BM25 Top 30、RRF(k=60)、Rerank Top 10、Context Top 6；
- 样本：8 条可回答问题、3 条不可回答问题，由 Apifox 手工调用 `/retrieval/debug`；
- 本报告是阈值与失败模式探索，不是 40 条正式基线，不能用于宣称整体质量提升。

## 可回答样本

| 样本 | 期望证据数 | Context 排名 | 观察 |
|---|---:|---|---|
| retrieval-001 | 1 | 1 | 各阶段均第 1 |
| retrieval-020 | 1 | 1 | BM25 第 2，RRF/Rerank 第 1 |
| retrieval-023 | 1 | 1 | BM25 第 2，RRF/Rerank 第 1 |
| retrieval-026 | 1 | 1 | 各阶段均第 1 |
| retrieval-027 | 1 | 1 | 各阶段均第 1 |
| retrieval-028 | 1 | 1 | 各阶段均第 1 |
| retrieval-029 | 1 | 1 | Vector 第 15、BM25 第 1、RRF 第 2、Rerank 第 1 |
| retrieval-032 | 2 | 1、4 | 第二份跨文档证据由 RRF 第 2 降到 Rerank 第 4，但仍进入 Context |

按 9 个期望证据计算，Context Evidence Recall@1 为 8/9，Recall@3 为 8/9，Recall@6 为 9/9，证据级 MRR 为 0.9167。样本数较小，且不是随机抽样，指标只用于检查链路和识别失败模式。

## 不可回答样本

| 样本 | 类型 | 最高 Rerank 分数 | 观察 |
|---|---|---:|---|
| retrieval-036 | 域内但缺少准确数值 | 约 0.37 | 召回 QMS 背景，但没有 Recall@10 数值 |
| retrieval-040 | 域外问题 | 约 0.29 | 返回与贝尔不等式无关的内容 |
| retrieval-033 | 相关主题但不蕴含答案 | 约 0.77 | 资料只说明站内信，不能推断支持短信 |

前两条表明 Rerank Top-K 不能无门槛进入 Context，因此增加首版 `minimum-rerank-score=0.4`。第三条表明相关性分数不能判断证据是否蕴含答案，因此同时加强回答 Prompt：对能力、状态和准确数值问题必须有明确证据，相关背景不足以作肯定回答。

## 决定与待验证项

- `0.4` 只是首轮抽样阈值，必须在全部 32 条可回答和 8 条不可回答样本上校准；
- 阈值变更后应确认 retrieval-032 的第二份证据仍保留；
- `retrieval-033` 必须在 RAG 最终回答层验证拒答，不能用“Context 是否为空”替代证据充分性评测；
- 正式报告应记录数据集与 manifest SHA-256、Git commit、实际模型版本、阶段 P50/P95、Token 与费用；
- 在正式基线完成前，不进入 LangGraph4j、MCP 或 Skill 实施阶段；Agent Runtime 的领域模型设计可在不改变 RAG 基线的前提下开始。

## 2026-11-19 门槛回归

启用 `minimum-rerank-score=0.4` 后重新执行三个不可回答样本：

| 样本 | Rerank 过滤前 | 过滤后 | Context | 结果 |
|---|---:|---:|---:|---|
| retrieval-040 贝尔不等式 | 10 | 0 | 0 | 低相关域外证据已被确定性过滤 |
| retrieval-036 QMS Recall@10 数值 | 10 | 0 | 0 | 相关度不足且缺少数值的证据已被过滤 |
| retrieval-033 短信审批通知 | 10 | 9 | 6 | 仍召回站内信相关资料，需在最终回答层验证证据充分性拒答 |

前两条验证了首版门槛能够阻止已观察到的低相关误召回。第三条再次证明分数门槛不能判断“相关资料是否明确支持问题中的结论”，不得把检索命中直接记为回答失败或成功；必须检查 Prompt 1.1.0 的最终回答是否明确说明资料只记载站内信、未记载短信能力。

retrieval-033 随后通过 `/rag/stream` 完成最终回答回归：模型明确说明当前资料未记载短信支持，只能确认 `system_notify_*`、`NotifyMessageSendApi` 和审批通知模板对应站内信，并建议补充短信网关或多渠道路由资料后再判断。SSE 依次产生 `started`、`status`、`citations`、`delta` 和 `completed`，无错误终态。Prompt 1.1.0 的证据充分性规则在该样本上验证通过；正文曾把组合引用包在 Markdown 反引号中，属于展示格式瑕疵，不影响独立 citations 事件，后续 Prompt 迭代应禁止用代码格式包裹引用标记。
