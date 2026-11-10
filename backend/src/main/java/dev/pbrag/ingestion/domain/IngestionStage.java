package dev.pbrag.ingestion.domain;

/** 文档从待处理到可检索所经过的有序处理阶段。 */
public enum IngestionStage {
	PENDING,
	PARSING,
	CHUNKING,
	EMBEDDING,
	INDEXING,
	COMPLETED
}
