package dev.pbrag.knowledge.service.impl;

import dev.pbrag.knowledge.domain.DocumentListItem;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import dev.pbrag.knowledge.service.DocumentQueryService;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentQueryServiceImpl implements DocumentQueryService {

	private final KnowledgeBaseService knowledgeBaseService;
	private final DocumentMapper documentMapper;

	public DocumentQueryServiceImpl(
			KnowledgeBaseService knowledgeBaseService,
			DocumentMapper documentMapper) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.documentMapper = documentMapper;
	}

	@Override
	@Transactional(readOnly = true)
	public List<DocumentListItem> list(UUID workspaceId, UUID knowledgeBaseId) {
		knowledgeBaseService.require(workspaceId, knowledgeBaseId);
		return documentMapper.selectAllByKnowledgeBase(workspaceId, knowledgeBaseId);
	}
}
