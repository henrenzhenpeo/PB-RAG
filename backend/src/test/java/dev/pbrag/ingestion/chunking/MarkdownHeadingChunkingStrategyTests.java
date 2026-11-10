package dev.pbrag.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pbrag.ingestion.parser.ParsedBlock;
import dev.pbrag.ingestion.parser.ParsedDocument;
import java.util.List;
import org.junit.jupiter.api.Test;

class MarkdownHeadingChunkingStrategyTests {

	@Test
	void startsANewChunkWhenHeadingChanges() {
		var strategy = new MarkdownHeadingChunkingStrategy(1000, 1500);
		var document = new ParsedDocument("test", List.of(
				new ParsedBlock("# A", "A", 0, 3, 1, 1),
				new ParsedBlock("内容 A", "A", 5, 9, 3, 3),
				new ParsedBlock("# B", "B", 11, 14, 5, 5),
				new ParsedBlock("内容 B", "B", 16, 20, 7, 7)));

		List<ChunkDraft> chunks = strategy.split(document);

		assertThat(chunks).hasSize(2);
		assertThat(chunks.getFirst().content()).contains("# A", "内容 A");
		assertThat(chunks.getLast().headingPath()).isEqualTo("B");
		assertThat(chunks).extracting(ChunkDraft::sequenceNumber).containsExactly(0, 1);
	}

	@Test
	void splitsAnOversizedBlockDeterministically() {
		var strategy = new MarkdownHeadingChunkingStrategy(100, 120);
		String text = "x".repeat(250);
		var document = new ParsedDocument("test", List.of(
				new ParsedBlock(text, "Large", 10, 260, 2, 5)));

		List<ChunkDraft> chunks = strategy.split(document);

		assertThat(chunks).hasSize(3);
		assertThat(chunks).extracting(chunk -> chunk.content().length()).containsExactly(120, 120, 10);
		assertThat(chunks.getLast().endOffset()).isEqualTo(260);
	}
}
