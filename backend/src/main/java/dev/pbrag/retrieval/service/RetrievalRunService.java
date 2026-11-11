package dev.pbrag.retrieval.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import dev.pbrag.retrieval.domain.RetrievalStageResult;
import dev.pbrag.retrieval.domain.RetrievalStageSummary;
import dev.pbrag.retrieval.mapper.RetrievalRunMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 检索运行和阶段候选的事务持久化边界。 */
@Service
public class RetrievalRunService {

	private final RetrievalRunMapper mapper;
	private final ObjectMapper objectMapper;

	public RetrievalRunService(RetrievalRunMapper mapper, ObjectMapper objectMapper) {
		this.mapper = mapper;
		this.objectMapper = objectMapper;
	}

	/** 创建运行并保存实际生效的模型和检索参数。 */
	@Transactional
	public UUID start(
			UUID workspaceId, UUID knowledgeBaseId, String question,
			String embeddingModel, String rerankModel, Map<String, Object> configuration) {
		UUID id = UUID.randomUUID();
		if (mapper.insertRun(id, workspaceId, knowledgeBaseId, question,
				embeddingModel, rerankModel, json(configuration)) != 1) {
			throw new IllegalStateException("Failed to create retrieval run");
		}
		return id;
	}

	/** 将一个阶段的有序候选作为同一批次写入。 */
	@Transactional
	public void saveStage(UUID runId, UUID workspaceId, RetrievalStageSummary summary,
			List<RetrievalStageResult> candidates) {
		if (summary.candidateCount() != candidates.size()) {
			throw new IllegalArgumentException("Stage candidate count does not match its summary");
		}
		for (RetrievalStageResult candidate : candidates) {
			if (mapper.insertCandidate(
					UUID.randomUUID(), runId, workspaceId, candidate, json(candidate.metadata())) != 1) {
				throw new IllegalStateException("Failed to save retrieval candidate");
			}
		}
		if (mapper.insertStageSummary(runId, workspaceId, summary, json(summary.metadata())) != 1) {
			throw new IllegalStateException("Failed to save retrieval stage summary");
		}
	}

	/** 标记检索成功。 */
	@Transactional
	public void complete(UUID runId, long latencyMs, String finalStage, String degradationCode) {
		if (mapper.complete(runId, latencyMs, finalStage, degradationCode) != 1) {
			throw new IllegalStateException("Retrieval run is no longer running");
		}
	}

	/** 尽力保存失败终态，不覆盖原始检索异常。 */
	@Transactional
	public void fail(UUID runId, long latencyMs, String errorCode, Throwable error) {
		String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
		if (message.length() > 500) message = message.substring(0, 500);
		if (mapper.fail(runId, latencyMs, errorCode, message) != 1) {
			throw new IllegalStateException("Retrieval run is no longer running");
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JacksonException exception) {
			throw new IllegalStateException("Failed to serialize retrieval metadata", exception);
		}
	}
}
