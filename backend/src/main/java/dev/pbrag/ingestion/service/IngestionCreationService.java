package dev.pbrag.ingestion.service;

import java.util.UUID;

public interface IngestionCreationService {

	IngestionCreationResult create(
			UUID workspaceId, UUID knowledgeBaseId, String displayName,
			String contentSha256, String storageKey);
}
