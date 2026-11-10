package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.service.IngestionJobService;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 入库任务查询实现，不允许仅凭 Job ID 跨 Workspace 读取。 */
@Service
public class IngestionJobServiceImpl implements IngestionJobService {

	private final IngestionJobMapper mapper;

	/** 注入入库任务 Mapper。 */
	public IngestionJobServiceImpl(IngestionJobMapper mapper) {
		this.mapper = mapper;
	}

	/** 在 Workspace 作用域内查询任务并统一处理不存在语义。 */
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
