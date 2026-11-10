package dev.pbrag.retrieval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.mapper.ChunkRetrievalMapper;
import dev.pbrag.retrieval.service.impl.RetrievalServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/** 基础向量检索的作用域、参数传递与维度保护回归测试。 */
class RetrievalServiceTests {

	@Test
	void validatesScopeAndQueriesWithTheQuestionEmbedding() {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
		ChunkRetrievalMapper mapper = mock(ChunkRetrievalMapper.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		float[] embedding = new float[] { 0.25f, -0.5f, 0.75f };
		RetrievalHit hit = new RetrievalHit(
				UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"README.md", 0, "evidence", "{}", 0.91);
		when(embeddingModel.embed("如何访问接口？")).thenReturn(embedding);
		when(mapper.searchCurrentVersions(
				workspaceId, knowledgeBaseId, "[0.25,-0.5,0.75]", 0.2, 6))
				.thenReturn(List.of(hit));
		RetrievalService service = new RetrievalServiceImpl(
				knowledgeBaseService, mapper, embeddingModel, 3, 6, 0.2);

		assertThat(service.search(workspaceId, knowledgeBaseId, "  如何访问接口？  "))
				.containsExactly(hit);
		verify(knowledgeBaseService).require(workspaceId, knowledgeBaseId);
		verify(mapper).searchCurrentVersions(
				workspaceId, knowledgeBaseId, "[0.25,-0.5,0.75]", 0.2, 6);
	}

	@Test
	void rejectsUnexpectedEmbeddingDimensionsBeforeQueryingPostgres() {
		KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
		ChunkRetrievalMapper mapper = mock(ChunkRetrievalMapper.class);
		EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
		when(embeddingModel.embed(anyString())).thenReturn(new float[] { 1f, 2f });
		RetrievalService service = new RetrievalServiceImpl(
				knowledgeBaseService, mapper, embeddingModel, 3, 6, 0.2);

		assertThatThrownBy(() -> service.search(UUID.randomUUID(), UUID.randomUUID(), "question"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("expected 3 but got 2");
	}
}
