package dev.pbrag.retrieval.domain;

import java.util.Map;

/** 即使阶段没有候选，也保留其状态、耗时、错误和实际配置。 */
public record RetrievalStageSummary(
		RetrievalStage stage,
		RetrievalStageStatus status,
		int candidateCount,
		long latencyMs,
		String errorCode,
		Map<String, Object> metadata) {
}
