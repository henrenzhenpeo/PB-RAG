package dev.pbrag.knowledge.service;

import dev.pbrag.knowledge.domain.DocumentListItem;
import java.util.List;
import java.util.UUID;

public interface DocumentQueryService {

	List<DocumentListItem> list(UUID workspaceId, UUID knowledgeBaseId);
}
