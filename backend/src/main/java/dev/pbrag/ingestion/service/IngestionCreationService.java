package dev.pbrag.ingestion.service;

import java.util.UUID;

/** 在单个数据库事务内创建或复用文档版本及其入库任务。 */
public interface IngestionCreationService {

	/** 原子创建或复用文档、版本和入库任务，并返回本次创建结果。 */
	IngestionCreationResult create(
			UUID workspaceId, UUID knowledgeBaseId, String displayName, String mediaType,
			String contentSha256, String storageKey);
}
