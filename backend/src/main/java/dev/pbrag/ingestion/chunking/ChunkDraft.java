package dev.pbrag.ingestion.chunking;

/** 尚未落库的 Chunk，保留稳定序号及原文字符、行号和页码范围。 */
public record ChunkDraft(
		int sequenceNumber,
		String content,
		String headingPath,
		int startOffset,
		int endOffset,
		int startLine,
		int endLine,
		Integer startPage,
		Integer endPage) {
}
