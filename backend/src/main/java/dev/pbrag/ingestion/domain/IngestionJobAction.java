package dev.pbrag.ingestion.domain;

/** 状态机允许执行的领域动作，由 IngestionJobState 统一验证。 */
public enum IngestionJobAction {
	START_PARSING,
	START_CHUNKING,
	START_EMBEDDING,
	START_INDEXING,
	COMPLETE,
	WAIT_FOR_RETRY,
	FAIL,
	RETRY,
	CANCEL
}
