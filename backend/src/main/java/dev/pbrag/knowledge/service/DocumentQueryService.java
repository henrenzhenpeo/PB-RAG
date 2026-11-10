package dev.pbrag.knowledge.service;

import dev.pbrag.knowledge.domain.DocumentListItem;
import java.util.List;
import java.util.UUID;

/** 文档只读应用服务，保证查询始终携带 Workspace 与知识库作用域。 */
public interface DocumentQueryService {

	/** 查询知识库文档及其当前版本和最新入库任务状态。 */
	List<DocumentListItem> list(UUID workspaceId, UUID knowledgeBaseId);
}
