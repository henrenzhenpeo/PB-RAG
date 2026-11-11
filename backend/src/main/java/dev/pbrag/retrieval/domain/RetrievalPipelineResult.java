package dev.pbrag.retrieval.domain;

import java.util.List;
import java.util.UUID;

/** 一次可观察的 Production RAG 检索运行，包含最终证据和全部阶段快照。 */
public record RetrievalPipelineResult(
		UUID runId,
		String finalStage,
		String degradationCode,
		List<RetrievalHit> hits,
		List<RetrievalStageResult> stages,
		List<RetrievalStageSummary> stageSummaries) {
}
