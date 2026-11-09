package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

public record DocumentUploadResult(
		UUID documentId,
		UUID documentVersionId,
		IngestionJob job,
		String contentSha256,
		boolean duplicate) {
}
