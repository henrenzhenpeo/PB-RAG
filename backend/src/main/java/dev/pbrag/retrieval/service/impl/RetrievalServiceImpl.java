package dev.pbrag.retrieval.service.impl;

import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.platform.api.InvalidRequestException;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.mapper.ChunkRetrievalMapper;
import dev.pbrag.retrieval.service.RetrievalService;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 基础向量检索实现，负责问题向量化、资源校验和 pgvector Top-K 查询。 */
@Service
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai")
public class RetrievalServiceImpl implements RetrievalService {

	private final KnowledgeBaseService knowledgeBaseService;
	private final ChunkRetrievalMapper mapper;
	private final EmbeddingModel embeddingModel;
	private final int embeddingDimensions;
	private final int topK;
	private final double minimumSimilarity;

	/** 注入知识库校验、检索 Mapper、模型及检索参数。 */
	public RetrievalServiceImpl(
			KnowledgeBaseService knowledgeBaseService,
			ChunkRetrievalMapper mapper,
			EmbeddingModel embeddingModel,
			@Value("${pbrag.retrieval.embedding-dimensions:1024}") int embeddingDimensions,
			@Value("${pbrag.retrieval.top-k:6}") int topK,
			@Value("${pbrag.retrieval.minimum-similarity:0.2}") double minimumSimilarity) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.mapper = mapper;
		this.embeddingModel = embeddingModel;
		this.embeddingDimensions = embeddingDimensions;
		if (topK < 1 || topK > 50) throw new IllegalArgumentException("Retrieval top-k must be between 1 and 50");
		if (minimumSimilarity < -1 || minimumSimilarity > 1) {
			throw new IllegalArgumentException("Minimum similarity must be between -1 and 1");
		}
		this.topK = topK;
		this.minimumSimilarity = minimumSimilarity;
	}

	/**
	 * 先验证父资源，再生成查询向量；维度不匹配时立即失败，避免把模型配置错误
	 * 伪装成“没有检索结果”。
	 */
	@Override
	public List<RetrievalHit> search(UUID workspaceId, UUID knowledgeBaseId, String question) {
		String normalizedQuestion = normalizeQuestion(question);
		knowledgeBaseService.require(workspaceId, knowledgeBaseId);
		float[] embedding = embeddingModel.embed(normalizedQuestion);
		if (embedding.length != embeddingDimensions) {
			throw new IllegalStateException(
					"Query embedding dimension mismatch: expected %d but got %d"
							.formatted(embeddingDimensions, embedding.length));
		}
		return mapper.searchCurrentVersions(
				workspaceId, knowledgeBaseId, vectorLiteral(embedding), minimumSimilarity, topK);
	}

	/** 校验问题非空并限制长度，避免无意义或失控的模型请求。 */
	private static String normalizeQuestion(String question) {
		if (question == null || question.isBlank()) throw new InvalidRequestException("问题不能为空");
		String value = question.strip();
		if (value.length() > 2000) throw new InvalidRequestException("问题不能超过 2000 个字符");
		return value;
	}

	/** 将查询向量编码为 PostgreSQL pgvector 可解析的文本字面量。 */
	private static String vectorLiteral(float[] values) {
		StringBuilder result = new StringBuilder(values.length * 10).append('[');
		for (int index = 0; index < values.length; index++) {
			if (index > 0) result.append(',');
			result.append(values[index]);
		}
		return result.append(']').toString();
	}
}
