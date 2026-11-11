package dev.pbrag.retrieval.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.domain.RetrievalStage;
import dev.pbrag.retrieval.mapper.ChunkRetrievalMapper;
import dev.pbrag.retrieval.rerank.RerankClient;
import dev.pbrag.retrieval.rerank.RerankTransientException;
import dev.pbrag.retrieval.service.impl.RetrievalServiceImpl;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

/** Production RAG 的作用域、双路召回、RRF 去重与维度保护测试。 */
class RetrievalServiceTests {

	@Test
	void fusesVectorAndKeywordRanksAndKeepsOneCandidatePerChunk() {
		Fixture fixture = new Fixture();
		RetrievalHit shared = hit(0.90);
		RetrievalHit vectorOnly = hit(0.80);
		RetrievalHit keywordOnly = hit(0.95);
		when(fixture.embeddingModel.embed("如何访问接口？")).thenReturn(new float[] { 0.25f, -0.5f, 0.75f });
		when(fixture.mapper.searchCurrentVersions(fixture.workspaceId, fixture.knowledgeBaseId,
				"[0.25,-0.5,0.75]", 0.2, 30)).thenReturn(List.of(shared, vectorOnly));
		when(fixture.mapper.searchBm25CurrentVersions(fixture.workspaceId, fixture.knowledgeBaseId,
				"如何访问接口？", 30)).thenReturn(List.of(keywordOnly, shared));

		var result = fixture.service().searchDetailed(
				fixture.workspaceId, fixture.knowledgeBaseId, "  如何访问接口？  ");

		assertThat(result.hits()).extracting(RetrievalHit::chunkId)
				.containsExactly(shared.chunkId(), keywordOnly.chunkId(), vectorOnly.chunkId());
		assertThat(result.stages().stream().filter(value -> value.stage() == RetrievalStage.FUSION))
				.hasSize(3);
		assertThat(result.stages().stream()
				.filter(value -> value.stage() == RetrievalStage.KEYWORD)
				.flatMap(value -> value.metadata().keySet().stream()))
				.containsOnly("bm25Score");
		verify(fixture.knowledgeBaseService).require(fixture.workspaceId, fixture.knowledgeBaseId);
		verify(fixture.runService).complete(any(UUID.class), any(Long.class));
	}

	@Test
	void rejectsUnexpectedEmbeddingDimensionsAndMarksRunFailed() {
		Fixture fixture = new Fixture();
		when(fixture.embeddingModel.embed(anyString())).thenReturn(new float[] { 1f, 2f });

		assertThatThrownBy(() -> fixture.service().search(
				fixture.workspaceId, fixture.knowledgeBaseId, "question"))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("expected 3 but got 2");
		verify(fixture.runService).fail(any(UUID.class), any(Long.class),
				anyString(), any(IllegalStateException.class));
	}

	@Test
	void appliesRerankOrderWhenProviderSucceeds() {
		Fixture fixture = new Fixture();
		RetrievalHit first = hit(0.9);
		RetrievalHit second = hit(0.8);
		when(fixture.embeddingModel.embed(anyString())).thenReturn(new float[] { 1f, 2f, 3f });
		when(fixture.mapper.searchCurrentVersions(any(), any(), anyString(), anyDouble(), anyInt()))
				.thenReturn(List.of(first, second));
		when(fixture.mapper.searchBm25CurrentVersions(any(), any(), anyString(), anyInt()))
				.thenReturn(List.of());
		when(fixture.rerankClient.enabled()).thenReturn(true);
		when(fixture.rerankClient.model()).thenReturn("rerank-model");
		when(fixture.rerankClient.rerank(anyString(), any(), anyInt()))
				.thenReturn(List.of(new RerankClient.RerankScore(1, 0.99), new RerankClient.RerankScore(0, 0.7)));

		var result = fixture.service().searchDetailed(fixture.workspaceId, fixture.knowledgeBaseId, "question");

		assertThat(result.finalStage()).isEqualTo("RERANK");
		assertThat(result.hits()).extracting(RetrievalHit::chunkId)
				.containsExactly(second.chunkId(), first.chunkId());
	}

	@Test
	void degradesToFusionOrderWhenRerankHasTransientFailure() {
		Fixture fixture = new Fixture();
		RetrievalHit first = hit(0.9);
		when(fixture.embeddingModel.embed(anyString())).thenReturn(new float[] { 1f, 2f, 3f });
		when(fixture.mapper.searchCurrentVersions(any(), any(), anyString(), anyDouble(), anyInt()))
				.thenReturn(List.of(first));
		when(fixture.mapper.searchBm25CurrentVersions(any(), any(), anyString(), anyInt()))
				.thenReturn(List.of());
		when(fixture.rerankClient.enabled()).thenReturn(true);
		when(fixture.rerankClient.rerank(anyString(), any(), anyInt()))
				.thenThrow(new RerankTransientException("temporary", new RuntimeException()));

		var result = fixture.service().searchDetailed(fixture.workspaceId, fixture.knowledgeBaseId, "question");

		assertThat(result.finalStage()).isEqualTo("FUSION_RERANK_DEGRADED");
		assertThat(result.hits()).extracting(RetrievalHit::chunkId).containsExactly(first.chunkId());
		assertThat(result.stages()).noneMatch(value -> value.stage() == RetrievalStage.RERANK);
	}

	private static RetrievalHit hit(double score) {
		return new RetrievalHit(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"README.md", 0, "evidence", "{}", score);
	}

	private static final class Fixture {
		private final UUID workspaceId = UUID.randomUUID();
		private final UUID knowledgeBaseId = UUID.randomUUID();
		private final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
		private final ChunkRetrievalMapper mapper = mock(ChunkRetrievalMapper.class);
		private final RetrievalRunService runService = mock(RetrievalRunService.class);
		private final RerankClient rerankClient = mock(RerankClient.class);
		private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

		private Fixture() {
			when(runService.start(any(), any(), anyString(), anyString(), anyString(), any()))
					.thenReturn(UUID.randomUUID());
			when(rerankClient.model()).thenReturn("disabled");
			when(rerankClient.enabled()).thenReturn(false);
		}

		private RetrievalService service() {
			return new RetrievalServiceImpl(knowledgeBaseService, mapper, runService,
					rerankClient, embeddingModel, "embedding-model", 3,
					30, 30, 10, 6, 60, 0.2);
		}
	}
}
