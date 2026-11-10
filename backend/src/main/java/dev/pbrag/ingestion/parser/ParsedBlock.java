package dev.pbrag.ingestion.parser;

/** 解析后的最小结构块，携带标题路径与可追溯原文位置。 */
public record ParsedBlock(
		String content,
		String headingPath,
		int startOffset,
		int endOffset,
		int startLine,
		int endLine,
		Integer pageNumber) {

	public ParsedBlock(
			String content, String headingPath, int startOffset, int endOffset,
			int startLine, int endLine) {
		this(content, headingPath, startOffset, endOffset, startLine, endLine, null);
	}
}
