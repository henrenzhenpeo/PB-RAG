package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

/** 数据库事务创建文档、版本与任务后的结果；duplicate 表示复用了已有内容版本。 */
public record IngestionCreationResult(
		UUID documentId,
		UUID documentVersionId,
		IngestionJob job,
		boolean duplicate) {
}
