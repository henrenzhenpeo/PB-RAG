package dev.pbrag.ingestion.chunking;

import dev.pbrag.ingestion.parser.ParsedDocument;
import java.util.List;

/** 将结构化解析块转换为可索引 Chunk 的版本化策略。 */
public interface ChunkingStrategy {

	/** 返回会参与索引复现记录的分块策略版本。 */
	String version();
	/** 将结构化解析结果切分为有序、可索引的 Chunk 草稿。 */
	List<ChunkDraft> split(ParsedDocument document);
}
