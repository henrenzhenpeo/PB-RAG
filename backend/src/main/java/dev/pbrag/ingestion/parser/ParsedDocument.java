package dev.pbrag.ingestion.parser;

import java.util.List;

/** 文档解析器的版本化输出，用于重现后续分块与引用定位。 */
public record ParsedDocument(String parserVersion, List<ParsedBlock> blocks) {

	public ParsedDocument {
		blocks = List.copyOf(blocks);
	}
}
