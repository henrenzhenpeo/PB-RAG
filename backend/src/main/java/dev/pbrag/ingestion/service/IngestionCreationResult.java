package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

public record IngestionCreationResult(
		UUID documentId,
		UUID documentVersionId,
		IngestionJob job,
		boolean duplicate) {
}
