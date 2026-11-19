# retrieval-v1 全量基线（待优化）

## 运行信息

- 日期：2026-11-19；
- 数据集：`retrieval-v1`，32 条可回答、8 条不可回答；
- 语料：4 个文档版本、377 个 Chunk；
- 流程：Vector Top 30、ParadeDB BM25 Top 30、RRF(k=60) Top 30、Rerank Top 10、Context Top 6；
- 模型：`qwen3.7-text-embedding-flash`、`qwen3.7-text-rerank`；
- 门槛：Vector 0.2、BM25 1.0、Rerank 0.4；
- 执行方式：通过 `/retrieval/debug` 单并发顺序调用；共 34 个期望证据；
- 限制：本轮由外部调用脚本即时聚合，尚未记录数据集/manifest SHA-256、Git commit、Token 和费用；延迟仅有平均值与最大值，未保存完整分布，因此暂不提供 P95。

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
