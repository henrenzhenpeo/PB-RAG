package dev.pbrag.ingestion.service;

import java.util.UUID;

/** 在单个数据库事务内创建或复用文档版本及其入库任务。 */
public interface IngestionCreationService {

	/** 原子创建或复用文档、版本和入库任务，并返回本次创建结果。 */
	IngestionCreationResult create(
			UUID workspaceId, UUID knowledgeBaseId, String displayName, String mediaType,
			String contentSha256, String storageKey);

	/** 为已有逻辑文档原子创建下一不可变版本；重复内容复用已有版本及任务。 */
	default IngestionCreationResult createVersion(
			UUID workspaceId, UUID knowledgeBaseId, UUID documentId, String mediaType,
			String contentSha256, String storageKey) {
		throw new UnsupportedOperationException("Document version creation is not implemented");
	}
}
