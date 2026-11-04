package dev.pbrag.ingestion.domain;

public enum IngestionStage {
	PENDING,
	PARSING,
	CHUNKING,
	EMBEDDING,
	INDEXING,
	COMPLETED
}
