package dev.pbrag.retrieval.domain;

/** 检索阶段的执行结果，区分正常完成、主动跳过和可降级失败。 */
public enum RetrievalStageStatus {
	COMPLETED,
	SKIPPED,
	DEGRADED
}
