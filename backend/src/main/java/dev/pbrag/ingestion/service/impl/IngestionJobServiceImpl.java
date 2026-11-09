package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.service.IngestionJobService;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionJobServiceImpl implements IngestionJobService {

	private final IngestionJobMapper mapper;

	public IngestionJobServiceImpl(IngestionJobMapper mapper) {
		this.mapper = mapper;
	}

	@Override
	@Transactional(readOnly = true)
	public IngestionJob require(UUID workspaceId, UUID jobId) {
		IngestionJob job = mapper.selectByWorkspaceIdAndId(workspaceId, jobId);
		if (job == null) {
			throw new ResourceNotFoundException("Ingestion job not found");
		}
		return job;
	}
}
