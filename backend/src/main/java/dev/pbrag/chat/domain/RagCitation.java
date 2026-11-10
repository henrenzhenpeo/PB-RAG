package dev.pbrag.chat.domain;

import java.util.UUID;

/** 回答引用，指向实际进入上下文的文档版本和 Chunk。 */
public record RagCitation(
		int index,
		UUID chunkId,
		UUID documentId,
		UUID documentVersionId,
		String documentName,
		int sequenceNumber,
		String sourcePosition,
		String excerpt,
		double similarity) {
}
