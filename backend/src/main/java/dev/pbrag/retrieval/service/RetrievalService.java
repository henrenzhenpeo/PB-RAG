package dev.pbrag.retrieval.service;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.util.List;
import java.util.UUID;

/** 为 RAG 和后续 Agent 工具提供统一、可追溯的知识检索入口。 */
public interface RetrievalService {

	/** 将问题向量化，并在知识库当前版本中返回相似度最高的证据。 */
	List<RetrievalHit> search(UUID workspaceId, UUID knowledgeBaseId, String question);
}
