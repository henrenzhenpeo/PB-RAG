package dev.pbrag.knowledge.service.impl;

import dev.pbrag.knowledge.domain.DocumentListItem;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import dev.pbrag.knowledge.service.DocumentQueryService;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 文档查询实现；先验证父资源，再执行双重作用域约束的 Mapper 查询。 */
@Service
public class DocumentQueryServiceImpl implements DocumentQueryService {

	private final KnowledgeBaseService knowledgeBaseService;
	private final DocumentMapper documentMapper;

	/** 注入知识库校验服务和文档查询 Mapper。 */
	public DocumentQueryServiceImpl(
			KnowledgeBaseService knowledgeBaseService,
			DocumentMapper documentMapper) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.documentMapper = documentMapper;
	}

	/** 验证父知识库后查询文档、当前版本和任务状态的组合投影。 */
	@Override
	@Transactional(readOnly = true)
	public List<DocumentListItem> list(UUID workspaceId, UUID knowledgeBaseId) {
		knowledgeBaseService.require(workspaceId, knowledgeBaseId);
		return documentMapper.selectAllByKnowledgeBase(workspaceId, knowledgeBaseId);
	}
}
