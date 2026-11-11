package dev.pbrag.retrieval.service.impl;

import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.platform.api.InvalidRequestException;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.domain.RetrievalPipelineResult;
import dev.pbrag.retrieval.domain.RetrievalStage;
import dev.pbrag.retrieval.domain.RetrievalStageResult;
import dev.pbrag.retrieval.domain.RetrievalStageStatus;
import dev.pbrag.retrieval.domain.RetrievalStageSummary;
import dev.pbrag.retrieval.mapper.ChunkRetrievalMapper;
import dev.pbrag.retrieval.rerank.RerankClient;
import dev.pbrag.retrieval.rerank.RerankProtocolException;
import dev.pbrag.retrieval.rerank.RerankTransientException;
import dev.pbrag.retrieval.service.RetrievalRunService;
import dev.pbrag.retrieval.service.RetrievalService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 自研 Production RAG：向量与关键词召回、RRF、可选重排及阶段持久化。 */
@Service
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai")
public class RetrievalServiceImpl implements RetrievalService {
	private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);
	private final KnowledgeBaseService knowledgeBaseService;
	private final ChunkRetrievalMapper mapper;
	private final RetrievalRunService runService;
	private final RerankClient rerankClient;
	private final EmbeddingModel embeddingModel;
	private final String embeddingModelName;
	private final int embeddingDimensions;
	private final int candidateTopK;
	private final int fusionTopK;
	private final int rerankTopK;
	private final int finalTopK;
	private final int rrfConstant;
	private final double minimumVectorSimilarity;
	private final double minimumBm25Score;
	private final int maximumContextCharacters;

	public RetrievalServiceImpl(KnowledgeBaseService knowledgeBaseService,
			ChunkRetrievalMapper mapper, RetrievalRunService runService,
			RerankClient rerankClient, EmbeddingModel embeddingModel,
			@Value("${spring.ai.openai.embedding.model:unknown}") String embeddingModelName,
			@Value("${pbrag.retrieval.embedding-dimensions:1024}") int embeddingDimensions,
			@Value("${pbrag.retrieval.candidate-top-k:30}") int candidateTopK,
			@Value("${pbrag.retrieval.fusion-top-k:30}") int fusionTopK,
			@Value("${pbrag.retrieval.rerank-top-k:10}") int rerankTopK,
			@Value("${pbrag.retrieval.top-k:6}") int finalTopK,
			@Value("${pbrag.retrieval.rrf-constant:60}") int rrfConstant,
			@Value("${pbrag.retrieval.minimum-similarity:0.2}") double minimumVectorSimilarity,
			@Value("${pbrag.retrieval.minimum-bm25-score:1.0}") double minimumBm25Score,
			@Value("${pbrag.rag.maximum-context-characters:12000}") int maximumContextCharacters) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.mapper = mapper;
		this.runService = runService;
		this.rerankClient = rerankClient;
		this.embeddingModel = embeddingModel;
		this.embeddingModelName = embeddingModelName;
		this.embeddingDimensions = embeddingDimensions;
		this.candidateTopK = positive("candidate-top-k", candidateTopK);
		this.fusionTopK = positive("fusion-top-k", fusionTopK);
		this.rerankTopK = positive("rerank-top-k", rerankTopK);
		this.finalTopK = positive("top-k", finalTopK);
		this.rrfConstant = positive("rrf-constant", rrfConstant);
		this.minimumVectorSimilarity = range("minimum-similarity", minimumVectorSimilarity, -1, 1);
		this.minimumBm25Score = nonNegative("minimum-bm25-score", minimumBm25Score);
		if (maximumContextCharacters < 1000) {
			throw new IllegalArgumentException("maximum-context-characters must be at least 1000");
		}
		this.maximumContextCharacters = maximumContextCharacters;
	}

	@Override
	public List<RetrievalHit> search(UUID workspaceId, UUID knowledgeBaseId, String question) {
		return searchDetailed(workspaceId, knowledgeBaseId, question).hits();
	}

	@Override
	public RetrievalPipelineResult searchDetailed(UUID workspaceId, UUID knowledgeBaseId, String question) {
		String query = normalizeQuestion(question);
		knowledgeBaseService.require(workspaceId, knowledgeBaseId);
		long started = System.nanoTime();
		UUID runId = runService.start(workspaceId, knowledgeBaseId, query, embeddingModelName,
				rerankClient.model(), configuration());
		try {
			float[] embedding = embeddingModel.embed(query);
			if (embedding.length != embeddingDimensions) throw new IllegalStateException(
					"Query embedding dimension mismatch: expected %d but got %d".formatted(embeddingDimensions, embedding.length));
			List<RetrievalStageResult> stages = new ArrayList<>();
			List<RetrievalStageSummary> summaries = new ArrayList<>();
			long stageStarted = System.nanoTime();
			List<RetrievalHit> vector = mapper.searchCurrentVersions(workspaceId, knowledgeBaseId,
					vectorLiteral(embedding), minimumVectorSimilarity, candidateTopK);
			save(runId, workspaceId, stages, summaries,
					summary(RetrievalStage.VECTOR, RetrievalStageStatus.COMPLETED, vector.size(),
							stageStarted, null, Map.of("minimumScore", minimumVectorSimilarity)),
					ranked(RetrievalStage.VECTOR, vector, "vectorScore"));
			stageStarted = System.nanoTime();
			List<RetrievalHit> rawKeyword = mapper.searchBm25CurrentVersions(workspaceId,
					knowledgeBaseId, query, candidateTopK);
			List<RetrievalHit> keyword = rawKeyword.stream()
					.filter(hit -> hit.similarity() >= minimumBm25Score).toList();
			save(runId, workspaceId, stages, summaries,
					summary(RetrievalStage.KEYWORD, RetrievalStageStatus.COMPLETED, keyword.size(),
							stageStarted, null, Map.of("minimumScore", minimumBm25Score,
									"unfilteredCandidateCount", rawKeyword.size())),
					ranked(RetrievalStage.KEYWORD, keyword, "bm25Score"));
			stageStarted = System.nanoTime();
			List<RetrievalStageResult> ordered = fuse(vector, keyword);
			save(runId, workspaceId, stages, summaries,
					summary(RetrievalStage.FUSION, RetrievalStageStatus.COMPLETED, ordered.size(),
							stageStarted, null, Map.of("rrfConstant", rrfConstant)), ordered);
			String finalStage = "FUSION";
			String degradationCode = null;
			if (rerankClient.enabled() && !ordered.isEmpty()) {
				stageStarted = System.nanoTime();
				try {
					ordered = rerank(query, ordered);
					save(runId, workspaceId, stages, summaries,
							summary(RetrievalStage.RERANK, RetrievalStageStatus.COMPLETED, ordered.size(),
									stageStarted, null, Map.of("model", rerankClient.model())), ordered);
					finalStage = "RERANK";
				}
				catch (RerankTransientException exception) {
					log.warn("rerank_degraded runId={} reason={}", runId, exception.getClass().getSimpleName());
					degradationCode = "RERANK_TRANSIENT_FAILURE";
					save(runId, workspaceId, stages, summaries,
							summary(RetrievalStage.RERANK, RetrievalStageStatus.DEGRADED, 0,
									stageStarted, degradationCode, Map.of("fallbackStage", "FUSION")), List.of());
				}
			}
			else {
				String skipCode = rerankClient.enabled() ? "RERANK_NO_CANDIDATES" : "RERANK_DISABLED";
				save(runId, workspaceId, stages, summaries,
						summary(RetrievalStage.RERANK, RetrievalStageStatus.SKIPPED, 0,
								System.nanoTime(), skipCode, Map.of()), List.of());
			}
			stageStarted = System.nanoTime();
			List<RetrievalStageResult> context = context(ordered);
			save(runId, workspaceId, stages, summaries,
					summary(RetrievalStage.CONTEXT, RetrievalStageStatus.COMPLETED, context.size(),
							stageStarted, null, Map.of("maximumCharacters", maximumContextCharacters)), context);
			runService.complete(runId, elapsedMillis(started), finalStage, degradationCode);
			return new RetrievalPipelineResult(runId, finalStage, degradationCode,
					context.stream().map(this::scoredHit).toList(), List.copyOf(stages), List.copyOf(summaries));
		}
		catch (RuntimeException exception) {
			try {
				runService.fail(runId, elapsedMillis(started), errorCode(exception), exception);
			}
			catch (RuntimeException persistenceFailure) {
				log.error("retrieval_run_failure_persistence_failed runId={} errorType={}",
						runId, persistenceFailure.getClass().getSimpleName());
			}
			throw exception;
		}
	}

	private void save(UUID runId, UUID workspaceId, List<RetrievalStageResult> all,
			List<RetrievalStageSummary> summaries, RetrievalStageSummary summary,
			List<RetrievalStageResult> stage) {
		runService.saveStage(runId, workspaceId, summary, stage);
		all.addAll(stage);
		summaries.add(summary);
	}

	private List<RetrievalStageResult> ranked(RetrievalStage stage, List<RetrievalHit> hits, String scoreName) {
		List<RetrievalStageResult> result = new ArrayList<>();
		for (int i = 0; i < hits.size(); i++) {
			RetrievalHit hit = hits.get(i);
			result.add(new RetrievalStageResult(stage, i + 1, hit.similarity(), hit, Map.of(scoreName, hit.similarity())));
		}
		return List.copyOf(result);
	}

	/** 用排名而非异构原始分数融合，并以稳定次级排序保证结果可复现。 */
	private List<RetrievalStageResult> fuse(List<RetrievalHit> vector, List<RetrievalHit> keyword) {
		Map<UUID, FusionAccumulator> candidates = new LinkedHashMap<>();
		accumulate(candidates, vector, true);
		accumulate(candidates, keyword, false);
		List<FusionAccumulator> ordered = candidates.values().stream()
				.sorted(Comparator.comparingDouble(FusionAccumulator::score).reversed()
						.thenComparingInt(FusionAccumulator::bestRank)
						.thenComparing(value -> value.hit.chunkId()))
				.limit(fusionTopK).toList();
		List<RetrievalStageResult> result = new ArrayList<>();
		for (int i = 0; i < ordered.size(); i++) {
			FusionAccumulator value = ordered.get(i);
			Map<String, Object> metadata = new HashMap<>();
			if (value.vectorRank != null) metadata.put("vectorRank", value.vectorRank);
			if (value.keywordRank != null) metadata.put("keywordRank", value.keywordRank);
			metadata.put("rrfConstant", rrfConstant);
			result.add(new RetrievalStageResult(RetrievalStage.FUSION, i + 1, value.score, value.hit, Map.copyOf(metadata)));
		}
		return List.copyOf(result);
	}

	private void accumulate(Map<UUID, FusionAccumulator> candidates, List<RetrievalHit> hits, boolean vector) {
		for (int i = 0; i < hits.size(); i++) {
			int rank = i + 1;
			RetrievalHit hit = hits.get(i);
			FusionAccumulator value = candidates.computeIfAbsent(hit.chunkId(), ignored -> new FusionAccumulator(hit));
			value.score += 1.0 / (rrfConstant + rank);
			if (vector) value.vectorRank = rank; else value.keywordRank = rank;
		}
	}

	private List<RetrievalStageResult> rerank(String query, List<RetrievalStageResult> fusion) {
		List<RetrievalHit> hits = fusion.stream().map(RetrievalStageResult::hit).toList();
		List<RerankClient.RerankScore> scores = rerankClient.rerank(query, hits, rerankTopK);
		List<RetrievalStageResult> result = new ArrayList<>();
		for (int i = 0; i < scores.size(); i++) {
			RerankClient.RerankScore score = scores.get(i);
			result.add(new RetrievalStageResult(RetrievalStage.RERANK, i + 1, score.score(), hits.get(score.index()),
					Map.of("fusionRank", score.index() + 1, "model", rerankClient.model())));
		}
		return List.copyOf(result);
	}

	private List<RetrievalStageResult> context(List<RetrievalStageResult> ordered) {
		List<RetrievalStageResult> result = new ArrayList<>();
		int characters = 0;
		for (int i = 0; i < Math.min(finalTopK, ordered.size()); i++) {
			RetrievalStageResult item = ordered.get(i);
			int candidateCharacters = item.hit().content().length();
			if (!result.isEmpty() && characters + candidateCharacters > maximumContextCharacters) break;
			result.add(new RetrievalStageResult(RetrievalStage.CONTEXT, i + 1, item.score(), item.hit(),
					Map.of("sourceStage", item.stage().name(), "sourceRank", item.rank())));
			characters += candidateCharacters;
		}
		return List.copyOf(result);
	}

	private RetrievalHit scoredHit(RetrievalStageResult value) {
		RetrievalHit hit = value.hit();
		return new RetrievalHit(hit.chunkId(), hit.documentId(), hit.documentVersionId(), hit.documentName(),
				hit.sequenceNumber(), hit.content(), hit.sourcePosition(), value.score());
	}

	private Map<String, Object> configuration() {
		return Map.of("candidateTopK", candidateTopK, "fusionTopK", fusionTopK,
				"rerankTopK", rerankTopK, "finalTopK", finalTopK, "rrfConstant", rrfConstant,
				"minimumVectorSimilarity", minimumVectorSimilarity,
				"minimumBm25Score", minimumBm25Score,
				"maximumContextCharacters", maximumContextCharacters,
				"keywordRetriever", "PARADEDB_BM25_CHINESE_LINDERA",
				"rerankEnabled", rerankClient.enabled());
	}

	private static String normalizeQuestion(String question) {
		if (question == null || question.isBlank()) throw new InvalidRequestException("问题不能为空");
		String value = question.strip();
		if (value.length() > 2000) throw new InvalidRequestException("问题不能超过 2000 个字符");
		return value;
	}

	private static String vectorLiteral(float[] values) {
		StringBuilder result = new StringBuilder(values.length * 10).append('[');
		for (int i = 0; i < values.length; i++) { if (i > 0) result.append(','); result.append(values[i]); }
		return result.append(']').toString();
	}

	private static int positive(String name, int value) {
		if (value < 1) throw new IllegalArgumentException(name + " must be positive");
		return value;
	}

	private static double range(String name, double value, double minimum, double maximum) {
		if (value < minimum || value > maximum) throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
		return value;
	}

	private static double nonNegative(String name, double value) {
		if (!Double.isFinite(value) || value < 0) throw new IllegalArgumentException(name + " must be finite and non-negative");
		return value;
	}

	private static RetrievalStageSummary summary(RetrievalStage stage, RetrievalStageStatus status,
			int candidateCount, long started, String errorCode, Map<String, Object> metadata) {
		return new RetrievalStageSummary(stage, status, candidateCount, elapsedMillis(started), errorCode, metadata);
	}

	private static long elapsedMillis(long started) { return (System.nanoTime() - started) / 1_000_000; }
	private static String errorCode(RuntimeException error) {
		if (error instanceof RerankProtocolException) return "RERANK_PROTOCOL_ERROR";
		return "RETRIEVAL_FAILED";
	}

	private final class FusionAccumulator {
		private final RetrievalHit hit;
		private Integer vectorRank;
		private Integer keywordRank;
		private double score;
		private FusionAccumulator(RetrievalHit hit) { this.hit = hit; }
		private double score() { return score; }
		private int bestRank() {
			return Math.min(vectorRank == null ? Integer.MAX_VALUE : vectorRank,
					keywordRank == null ? Integer.MAX_VALUE : keywordRank);
		}
	}
}
