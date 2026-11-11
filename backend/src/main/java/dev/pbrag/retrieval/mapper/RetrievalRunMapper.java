package dev.pbrag.retrieval.mapper;

import dev.pbrag.retrieval.domain.RetrievalStageResult;
import dev.pbrag.retrieval.domain.RetrievalStageSummary;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 持久化检索运行及各阶段候选，支持质量诊断和评测回放。 */
@Mapper
public interface RetrievalRunMapper {

	/** 创建一条处于 RUNNING 的检索运行。 */
	int insertRun(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("question") String question,
			@Param("embeddingModel") String embeddingModel,
			@Param("rerankModel") String rerankModel,
			@Param("configuration") String configuration);

	/** 保存一条阶段候选，不重复保存正文。 */
	int insertCandidate(
			@Param("id") UUID id,
			@Param("runId") UUID runId,
			@Param("workspaceId") UUID workspaceId,
			@Param("candidate") RetrievalStageResult candidate,
			@Param("metadata") String metadata);

	/** 保存阶段摘要，使零候选、跳过和降级仍然可观察。 */
	int insertStageSummary(
			@Param("runId") UUID runId,
			@Param("workspaceId") UUID workspaceId,
			@Param("summary") RetrievalStageSummary summary,
			@Param("metadata") String metadata);

	/** 将运行原子收敛为成功终态。 */
	int complete(
			@Param("id") UUID id,
			@Param("latencyMs") long latencyMs,
			@Param("finalStage") String finalStage,
			@Param("degradationCode") String degradationCode);

	/** 将运行原子收敛为失败终态并保存受限错误。 */
	int fail(
			@Param("id") UUID id,
			@Param("latencyMs") long latencyMs,
			@Param("errorCode") String errorCode,
			@Param("errorMessage") String errorMessage);
}
