package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在同一事务中完成入库任务并把已建好索引的版本切换为文档当前版本。 */
@Service
public class IngestionCompletionService {
	private final IngestionJobMapper jobMapper;
	private final DocumentMapper documentMapper;

	/** 注入任务与文档条件更新 Mapper。 */
	public IngestionCompletionService(IngestionJobMapper jobMapper, DocumentMapper documentMapper) {
		this.jobMapper = jobMapper;
		this.documentMapper = documentMapper;
	}

	/** 任一条件更新失败都会回滚，避免任务完成状态与生产检索版本不一致。 */
	@Transactional
	public void complete(
			UUID jobId, String workerId, UUID workspaceId, UUID documentId, UUID versionId) {
		if (documentMapper.updateCurrentVersion(workspaceId, documentId, versionId) != 1) {
			throw new IllegalStateException("Document was lost before selecting the completed version");
		}
		if (jobMapper.complete(jobId, workerId) != 1) {
			throw new IllegalStateException("Job lease was lost before completion");
		}
	}
}
