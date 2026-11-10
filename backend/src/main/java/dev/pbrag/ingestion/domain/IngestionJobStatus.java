package dev.pbrag.ingestion.domain;

/** 入库任务生命周期状态；阶段描述 RUNNING 内部的具体进度。 */
public enum IngestionJobStatus {
	PENDING,
	RUNNING,
	RETRY_WAIT,
	COMPLETED,
	FAILED,
	CANCELLED
}
