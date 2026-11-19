# retrieval-v1 全量基线（待优化）

## 运行信息

- 日期：2026-11-19；
- 数据集：`retrieval-v1`，32 条可回答、8 条不可回答；
- 语料：4 个文档版本、377 个 Chunk；
- 流程：Vector Top 30、ParadeDB BM25 Top 30、RRF(k=60) Top 30、Rerank Top 10、Context Top 6；
- 模型：`qwen3.7-text-embedding-flash`、`qwen3.7-text-rerank`；
- 门槛：Vector 0.2、BM25 1.0、Rerank 0.4；
- 执行方式：通过 `/retrieval/debug` 单并发顺序调用；共 34 个期望证据；
- Top 30 运行代码提交：`cf7039d`；数据集 SHA-256：`c264eaa481b49ba07d32c32208e5189e4623ceb52d5afa49b7be63ab765f7f2b`；manifest SHA-256：`84ec631b2b28d482586dc806bb4461fcf2f18b08cc005946ad8130cfd740b9d1`；
- 限制：Top 30 由外部调用脚本即时聚合，未保存完整延迟分布，因此没有 P95；供应商未返回可归集费用，本报告不记录估算成本。

## 检索指标

| 阶段 | Evidence Recall@1 | Recall@3 | Recall@6 | MRR |
|---|---:|---:|---:|---:|
| Vector | 70.59% | 85.29% | 88.24% | 0.79 |
| BM25 | 70.59% | 85.29% | 91.18% | 0.78 |
| RRF | 82.35% | 94.12% | 94.12% | 0.87 |
| Rerank | 88.24% | 88.24% | 94.12% | 0.90 |
| Context | 88.24% | 88.24% | 94.12% | 0.90 |

Rerank 提升了 Recall@1 和 MRR，但没有改善 Recall@6；部分跨文档证据由 RRF 前列下降到 Rerank 第 4，仍处于 Context Top 6。不能仅凭本轮结果宣称 Rerank 整体优于 RRF。

## 不可回答样本

- retrieval-036、037、038、039、040：Rerank 门槛过滤后 Context 为空；
- retrieval-033、034、035：保留主题相关证据，最终 RAG 回答均明确说明资料不足，没有从相邻能力推断肯定结论；
- 8/8 不可回答样本得到正确拒答语义；
- retrieval-039 首次调用返回 HTTP 500，重试后成功并得到空 Context；该瞬时失败仍需结合服务日志分类。

## 阻塞样本 retrieval-031

问题为“知识文档重新解析在 PRD 和功能验证说明中分别对应什么接口？”，需要两份跨文档证据。期望 PRD Chunk 在 BM25 第 30，期望功能验证 Chunk 在 Vector 第 28；两者均被 RRF Top 30 的双路高排名候选挤出，因此没有进入 Rerank 和 Context。

最终 Context 包含另一条有效 PRD 证据，能够回答 `POST /api/v1/knowledge/:id/reparse`，但缺少功能验证说明中的代理接口证据。最终 RAG 回答正确地说明只能回答 PRD 部分、无法可靠回答功能验证说明部分，没有编造缺失接口。失败分类为检索候选池/跨文档完整召回不足，而不是回答幻觉。

## 延迟与稳定性

- Vector 平均阶段耗时约 134.6 ms；
- BM25 平均阶段耗时约 33.2 ms；
- Rerank 平均阶段耗时约 616.6 ms；
- 观察到的 Rerank 最大耗时为 8,373 ms；
- 首轮请求错误 1/40，重试成功。正式基线需要持久化每条延迟后计算 P50/P95，并记录错误分类。

## 准入结论

暂不进入 Agent Runtime。下一步应针对 retrieval-031 比较以下最小改动：扩大 RRF/Rerank 候选池，或为 Vector/BM25 各保留最低候选配额。使用同一 40 条数据回归，要求 Context Recall@6 不低于当前 94.12%，retrieval-031 两份证据均进入 Top 6，8 条不可回答样本继续保持正确拒答，再固化正式基线。

## Fusion Top 60 单样本实验

将 `fusion-top-k` 从 30 临时提高到 60 后，retrieval-031 的实际去重融合候选为 53 条：

- 功能验证说明期望 Chunk：Vector 第 28、Fusion 第 50、Rerank/Context 第 3；
- PRD 期望 Chunk：BM25 第 30、Fusion 第 53、Rerank/Context 第 1；
- PRD 的另一条有效替代证据：Fusion 第 5、Rerank/Context 第 2。

扩大候选池解决了跨文档完整召回问题，证明失败点确实是 RRF Top 30 的提前截断，而不是 Rerank 最低分数。该样本 Rerank 阶段耗时由此前约 363 ms 增至 2,383 ms，约为 6.6 倍，因此不能仅凭单样本把 60 固化为默认值。下一步需要用相同 40 条数据比较 Top 30 与 Top 60 的 Recall、拒答、平均值和 P95；如果延迟成本不可接受，应把显式跨文档任务路由到后续 Agent `compare_documents`，而不是继续扩大固定 RAG 候选池。

## Fusion Top 60 全量 A/B

使用相同数据集、manifest、模型别名和门槛，以单并发顺序重新运行 40 条；所有请求一次成功，无重试或降级。实际去重 Fusion 候选平均 47.4 条，范围 39～59 条，Rerank 门槛后平均保留 7 条。

| 阶段 | Evidence Recall@1 | Recall@3 | Recall@6 | MRR |
|---|---:|---:|---:|---:|
| Vector | 70.59% | 85.29% | 91.18% | 0.79 |
| BM25 | 70.59% | 85.29% | 91.18% | 0.78 |
| RRF | 82.35% | 94.12% | 94.12% | 0.88 |
| Rerank | 91.18% | 94.12% | 100.00% | 0.94 |
| Context | 91.18% | 94.12% | 100.00% | 0.94 |

| 阶段 | 平均 | P50 | P95 | 最大 |
|---|---:|---:|---:|---:|
| Vector | 61.9 ms | 37 ms | 156 ms | 196 ms |
| BM25 | 36.0 ms | 33 ms | 63 ms | 69 ms |
| RRF | 0 ms | 0 ms | 0 ms | 0 ms |
| Rerank | 569.0 ms | 352 ms | 1,226 ms | 3,439 ms |

与 Top 30 相比，Rerank/Context Recall@6 从 94.12% 提升到 100%，MRR 从 0.90 提升到 0.94；retrieval-031 两份证据稳定进入 Context 第 3/1。8 条不可回答样本保持原有检索结果，其中 5 条为空 Context、3 条保留相关证据并已由最终回答验证正确拒答。

Top 60 的全量平均 Rerank 延迟没有高于 Top 30 的 616.6 ms，但两次运行的远程网络与供应商抖动不同，且 Top 30 缺少 P95，不能据此宣称扩大候选池降低延迟。可以确认的是本轮 Top 60 的 P95 为 1,226 ms、最大 3,439 ms，未出现错误；在当前正确性优先级下可接受。建议把 `fusion-top-k=60` 固化为首版基线，同时继续监控 Token/费用和真实 P95。
