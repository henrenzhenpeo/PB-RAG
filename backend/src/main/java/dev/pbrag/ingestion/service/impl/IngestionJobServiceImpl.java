package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.mapper.ChunkMapper;
import dev.pbrag.ingestion.service.IngestionJobService;
import dev.pbrag.ingestion.service.IngestionSignalService;
import dev.pbrag.ingestion.domain.IngestionJobStatus;
import dev.pbrag.ingestion.domain.IngestionJobAction;
import dev.pbrag.ingestion.domain.IngestionJobState;
import dev.pbrag.platform.api.InvalidRequestException;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 入库任务查询实现，不允许仅凭 Job ID 跨 Workspace 读取。 */
@Service
public class IngestionJobServiceImpl implements IngestionJobService {

	private final IngestionJobMapper mapper;
	private final ChunkMapper chunkMapper;
	private final IngestionSignalService signalService;

	/** 注入入库任务 Mapper。 */
	public IngestionJobServiceImpl(
			IngestionJobMapper mapper, ChunkMapper chunkMapper, IngestionSignalService signalService) {
		this.mapper = mapper;
		this.chunkMapper = chunkMapper;
		this.signalService = signalService;
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

	/** PostgreSQL 先进入取消终态，Redis 仅加速把取消信号传播给运行中的 Worker。 */
	@Override
	@Transactional
	public IngestionJob cancel(UUID workspaceId, UUID jobId) {
		IngestionJob current = require(workspaceId, jobId);
		if (current.status() == IngestionJobStatus.CANCELLED) return current;
		if (current.status() == IngestionJobStatus.COMPLETED || current.status() == IngestionJobStatus.FAILED) {
			throw new InvalidRequestException("A terminal ingestion job cannot be cancelled");
		}
		new IngestionJobState(current.status(), current.stage()).apply(IngestionJobAction.CANCEL);
		if (mapper.cancel(workspaceId, jobId) != 1) {
			throw new IllegalStateException("Ingestion job changed while it was being cancelled");
		}
		signalService.markCancelled(jobId);
		chunkMapper.deleteByDocumentVersion(workspaceId, current.documentVersionId());
		return require(workspaceId, jobId);
	}
}
