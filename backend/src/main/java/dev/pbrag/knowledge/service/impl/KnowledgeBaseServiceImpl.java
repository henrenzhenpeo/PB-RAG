package dev.pbrag.knowledge.service.impl;

import dev.pbrag.identity.service.WorkspaceService;
import dev.pbrag.knowledge.domain.KnowledgeBase;
import dev.pbrag.knowledge.mapper.KnowledgeBaseMapper;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.platform.api.ConflictException;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 知识库业务实现，负责参数归一化、存在性校验和事务。 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

	private final WorkspaceService workspaceService;
	private final KnowledgeBaseMapper knowledgeBaseMapper;

	public KnowledgeBaseServiceImpl(
			WorkspaceService workspaceService,
			KnowledgeBaseMapper knowledgeBaseMapper) {
		this.workspaceService = workspaceService;
		this.knowledgeBaseMapper = knowledgeBaseMapper;
	}

	@Override
	@Transactional
	public KnowledgeBase create(UUID workspaceId, String rawName, String rawDescription) {
		workspaceService.require(workspaceId);
		String name = rawName.strip();
		String description = rawDescription == null ? null : rawDescription.strip();
		KnowledgeBase knowledgeBase = new KnowledgeBase(
				UUID.randomUUID(), workspaceId, name, description);
		try {
			knowledgeBaseMapper.insert(knowledgeBase);
			return knowledgeBase;
		}
		catch (DuplicateKeyException exception) {
			throw new ConflictException(
					"Knowledge base name already exists in this workspace", exception);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<KnowledgeBase> list(UUID workspaceId) {
		workspaceService.require(workspaceId);
		return knowledgeBaseMapper.selectAllByWorkspaceId(workspaceId);
	}

	@Override
	@Transactional(readOnly = true)
	public KnowledgeBase require(UUID workspaceId, UUID knowledgeBaseId) {
		workspaceService.require(workspaceId);
		KnowledgeBase knowledgeBase = knowledgeBaseMapper.selectByWorkspaceIdAndId(
				workspaceId, knowledgeBaseId);
		if (knowledgeBase == null) {
			throw new ResourceNotFoundException("Knowledge base not found");
		}
		return knowledgeBase;
	}
}
