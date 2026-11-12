package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.service.impl.IngestionCreationServiceImpl;
import dev.pbrag.knowledge.domain.Document;
import dev.pbrag.knowledge.domain.DocumentVersion;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import dev.pbrag.knowledge.mapper.DocumentVersionMapper;
import dev.pbrag.knowledge.mapper.KnowledgeBaseMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IngestionCreationServiceTests {
	@Test
	void createsNextVersionWithoutSwitchingCurrentVersionBeforeIngestionCompletes() {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		DocumentMapper documentMapper = mock(DocumentMapper.class);
		DocumentVersionMapper versionMapper = mock(DocumentVersionMapper.class);
		IngestionJobMapper jobMapper = mock(IngestionJobMapper.class);
		when(documentMapper.lockByScope(workspaceId, knowledgeBaseId, documentId))
				.thenReturn(new Document(documentId, workspaceId, knowledgeBaseId, "guide.md", "text/markdown"));
		when(versionMapper.selectMaximumVersionNumber(workspaceId, documentId)).thenReturn(2);
		when(jobMapper.selectByWorkspaceIdAndId(any(), any())).thenAnswer(invocation ->
				new IngestionJob(invocation.getArgument(1), workspaceId, UUID.randomUUID()));
		IngestionCreationService service = new IngestionCreationServiceImpl(
				mock(KnowledgeBaseMapper.class), documentMapper, versionMapper, jobMapper);

		IngestionCreationResult result = service.createVersion(
				workspaceId, knowledgeBaseId, documentId, "text/markdown", "a".repeat(64), "v3.md");

		ArgumentCaptor<DocumentVersion> versionCaptor = ArgumentCaptor.forClass(DocumentVersion.class);
		verify(versionMapper).insert(versionCaptor.capture());
		assertThat(versionCaptor.getValue().versionNumber()).isEqualTo(3);
		assertThat(result.documentId()).isEqualTo(documentId);
		verify(documentMapper, org.mockito.Mockito.never()).updateCurrentVersion(any(), any(), any());
	}
}
