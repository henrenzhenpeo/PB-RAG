package dev.pbrag.ingestion.domain;

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
