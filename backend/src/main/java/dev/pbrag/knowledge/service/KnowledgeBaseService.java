package dev.pbrag.knowledge.service;

import dev.pbrag.knowledge.domain.KnowledgeBase;
import java.util.List;
import java.util.UUID;

/** 知识库应用服务，对控制器隐藏持久层实现。 */
public interface KnowledgeBaseService {

	KnowledgeBase create(UUID workspaceId, String rawName, String rawDescription);

	List<KnowledgeBase> list(UUID workspaceId);

	KnowledgeBase require(UUID workspaceId, UUID knowledgeBaseId);
}
