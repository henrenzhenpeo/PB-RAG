package dev.pbrag.knowledge.service;

import dev.pbrag.knowledge.domain.KnowledgeBase;
import java.util.List;
import java.util.UUID;

/** 知识库应用服务，对控制器隐藏持久层实现。 */
public interface KnowledgeBaseService {

	/** 在指定 Workspace 中校验并创建知识库。 */
	KnowledgeBase create(UUID workspaceId, String rawName, String rawDescription);
	/** 列出指定 Workspace 中的知识库。 */
	List<KnowledgeBase> list(UUID workspaceId);
	/** 按 Workspace 与知识库 ID 获取资源，不存在时抛出异常。 */
	KnowledgeBase require(UUID workspaceId, UUID knowledgeBaseId);
}
