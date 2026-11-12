package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.domain.IngestionJob;
import java.util.UUID;

/** Workspace 作用域内查询入库任务状态的应用服务。 */
public interface IngestionJobService {

	/** 在 Workspace 作用域内查询任务，不存在时抛出异常。 */
	IngestionJob require(UUID workspaceId, UUID jobId);

	/** 取消尚未完成的任务；重复取消返回同一个 CANCELLED 终态。 */
	IngestionJob cancel(UUID workspaceId, UUID jobId);
}
