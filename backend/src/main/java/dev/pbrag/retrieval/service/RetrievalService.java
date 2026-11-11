package dev.pbrag.retrieval.service;

import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.domain.RetrievalPipelineResult;
import java.util.List;
import java.util.UUID;

/** 为 RAG 和后续 Agent 工具提供统一、可追溯的知识检索入口。 */
public interface RetrievalService {

	/** 执行 Production RAG 流水线并返回最终上下文证据。 */
	List<RetrievalHit> search(UUID workspaceId, UUID knowledgeBaseId, String question);

	/** 执行同一流水线并返回用于调试和评测的阶段快照。 */
	RetrievalPipelineResult searchDetailed(UUID workspaceId, UUID knowledgeBaseId, String question);
}
