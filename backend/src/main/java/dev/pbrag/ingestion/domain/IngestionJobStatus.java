package dev.pbrag.ingestion.domain;

public enum IngestionJobStatus {
	PENDING,
	RUNNING,
	RETRY_WAIT,
	COMPLETED,
	FAILED,
	CANCELLED
}
