package dev.pbrag.knowledge.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.knowledge.domain.DocumentListItem;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import dev.pbrag.knowledge.service.impl.DocumentQueryServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DocumentQueryServiceTests {

	@Test
	void validatesKnowledgeBaseScopeBeforeQueryingDocuments() {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
		DocumentMapper documentMapper = mock(DocumentMapper.class);
		DocumentListItem item = new DocumentListItem();
		when(documentMapper.selectAllByKnowledgeBase(workspaceId, knowledgeBaseId))
				.thenReturn(List.of(item));

		DocumentQueryService service = new DocumentQueryServiceImpl(
				knowledgeBaseService, documentMapper);

		assertThat(service.list(workspaceId, knowledgeBaseId)).containsExactly(item);
		verify(knowledgeBaseService).require(workspaceId, knowledgeBaseId);
		verify(documentMapper).selectAllByKnowledgeBase(workspaceId, knowledgeBaseId);
	}
}
