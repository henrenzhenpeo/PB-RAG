package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

public interface IngestionJobService {

	IngestionJob require(UUID workspaceId, UUID jobId);
}
