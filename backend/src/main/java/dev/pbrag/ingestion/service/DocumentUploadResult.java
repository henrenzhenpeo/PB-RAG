package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

/** 上传接口结果，同时返回稳定资源 ID、任务快照、内容摘要和去重标记。 */
public record DocumentUploadResult(
		UUID documentId,
		UUID documentVersionId,
		IngestionJob job,
		String contentSha256,
		boolean duplicate) {
}
