package dev.pbrag.retrieval.domain;

import java.util.Map;

/** 某一检索阶段的一条有序候选及其阶段特有元数据。 */
public record RetrievalStageResult(
		RetrievalStage stage,
		int rank,
		double score,
		RetrievalHit hit,
		Map<String, Object> metadata) {
}
