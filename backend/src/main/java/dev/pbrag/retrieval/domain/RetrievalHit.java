package dev.pbrag.retrieval.domain;

import java.util.UUID;

/**
 * 一条可追溯的向量检索结果，绑定 Chunk、文档版本和稳定来源位置。
 */
public record RetrievalHit(
		UUID chunkId,
		UUID documentId,
		UUID documentVersionId,
		String documentName,
		int sequenceNumber,
		String content,
		String sourcePosition,
		double similarity) {
}
