package dev.pbrag.ingestion.chunking;

import dev.pbrag.ingestion.parser.ParsedBlock;
import dev.pbrag.ingestion.parser.ParsedDocument;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 标题感知的确定性分块策略。
 * 优先在标题切换或目标大小处分块，同时用硬上限切开超大单块，保留连续的来源位置。
 */
@Component
public class MarkdownHeadingChunkingStrategy implements ChunkingStrategy {

	private final int targetCharacters;
	private final int maximumCharacters;

	public MarkdownHeadingChunkingStrategy(
			@Value("${pbrag.ingestion.chunking.target-characters:1000}") int targetCharacters,
			@Value("${pbrag.ingestion.chunking.maximum-characters:1500}") int maximumCharacters) {
		if (targetCharacters < 100 || maximumCharacters < targetCharacters) {
			throw new IllegalArgumentException("Invalid chunk size configuration");
		}
		this.targetCharacters = targetCharacters;
		this.maximumCharacters = maximumCharacters;
	}

	/** 相同解析结果和配置必须产生相同序号、内容与来源范围，支持幂等重建。 */
	@Override
	public String version() {
		return "markdown-heading-v1";
	}

	@Override
	public List<ChunkDraft> split(ParsedDocument document) {
		List<ChunkDraft> chunks = new ArrayList<>();
		List<ParsedBlock> pending = new ArrayList<>();
		int size = 0;
		for (ParsedBlock block : document.blocks()) {
			boolean headingChanged = !pending.isEmpty()
					&& !pending.getFirst().headingPath().equals(block.headingPath());
			if (!pending.isEmpty() && (headingChanged || size >= targetCharacters
					|| size + block.content().length() + 2 > maximumCharacters)) {
				chunks.add(toChunk(chunks.size(), pending));
				pending.clear();
				size = 0;
			}
			if (block.content().length() > maximumCharacters) {
				if (!pending.isEmpty()) {
					chunks.add(toChunk(chunks.size(), pending));
					pending.clear();
					size = 0;
				}
				splitOversized(block, chunks);
				continue;
			}
			pending.add(block);
			size += block.content().length() + 2;
		}
		if (!pending.isEmpty()) chunks.add(toChunk(chunks.size(), pending));
		return List.copyOf(chunks);
	}

	/** 单个解析块超过硬上限时按字符窗口切分，避免模型请求和数据库行无限增大。 */
	private void splitOversized(ParsedBlock block, List<ChunkDraft> chunks) {
		String text = block.content();
		for (int start = 0; start < text.length(); start += maximumCharacters) {
			int end = Math.min(text.length(), start + maximumCharacters);
			chunks.add(new ChunkDraft(chunks.size(), text.substring(start, end), block.headingPath(),
					block.startOffset() + start, block.startOffset() + end,
					block.startLine(), block.endLine(), block.pageNumber(), block.pageNumber()));
		}
	}

	private static ChunkDraft toChunk(int sequence, List<ParsedBlock> blocks) {
		ParsedBlock first = blocks.getFirst();
		ParsedBlock last = blocks.getLast();
		String content = blocks.stream().map(ParsedBlock::content).reduce((a, b) -> a + "\n\n" + b).orElse("");
		return new ChunkDraft(sequence, content, last.headingPath(), first.startOffset(), last.endOffset(),
				first.startLine(), last.endLine(), first.pageNumber(), last.pageNumber());
	}
}
