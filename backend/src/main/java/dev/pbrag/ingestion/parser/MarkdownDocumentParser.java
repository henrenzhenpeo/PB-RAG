package dev.pbrag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.SourceSpan;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.text.TextContentRenderer;
import org.springframework.stereotype.Component;

/**
 * 基于 CommonMark AST 的 Markdown 解析器。
 * 保留块级 SourceSpan、标题层级和规范化后的字符偏移，使 Chunk 与引用可稳定回指原文。
 */
@Component
public class MarkdownDocumentParser implements DocumentParser {

	public static final String VERSION = "commonmark-0.22-heading-v1";
	private final Parser parser = Parser.builder()
			.includeSourceSpans(IncludeSourceSpans.BLOCKS)
			.build();
	private final TextContentRenderer textRenderer = TextContentRenderer.builder().build();

	/** 规范化换行和尾随空白后解析，确保不同操作系统上传同一文本得到一致位置。 */
	@Override
	public boolean supports(String storageKey) {
		return storageKey != null && storageKey.toLowerCase().endsWith(".md");
	}

	@Override
	public ParsedDocument parse(InputStream input) throws IOException {
		String markdown = normalize(new String(input.readAllBytes(), StandardCharsets.UTF_8));
		if (markdown.isBlank()) {
			throw new IllegalArgumentException("Markdown document contains no text");
		}

		Node root = parser.parse(markdown);
		int[] lineOffsets = lineOffsets(markdown);
		List<String> headings = new ArrayList<>();
		List<ParsedBlock> blocks = new ArrayList<>();
		for (Node node = root.getFirstChild(); node != null; node = node.getNext()) {
			if (node instanceof Heading heading) {
				updateHeadingPath(headings, heading.getLevel(), textRenderer.render(heading).strip());
			}
			ParsedBlock block = toBlock(markdown, lineOffsets, node, String.join(" > ", headings));
			if (block != null && !block.content().isBlank()) {
				blocks.add(block);
			}
		}
		if (blocks.isEmpty()) {
			throw new IllegalArgumentException("Markdown document produced no parseable blocks");
		}
		return new ParsedDocument(VERSION, blocks);
	}

	/** 把 CommonMark 的行列 SourceSpan 转换为 PB-RAG 使用的绝对字符与行号范围。 */
	private static ParsedBlock toBlock(String source, int[] lineOffsets, Node node, String headingPath) {
		List<SourceSpan> spans = node.getSourceSpans();
		if (spans.isEmpty()) return null;
		int start = spans.stream().mapToInt(span -> offset(lineOffsets, span)).min().orElse(0);
		int end = spans.stream().mapToInt(span -> offset(lineOffsets, span) + span.getLength()).max().orElse(start);
		int startLine = spans.stream().mapToInt(SourceSpan::getLineIndex).min().orElse(0) + 1;
		int endLine = spans.stream().mapToInt(SourceSpan::getLineIndex).max().orElse(startLine - 1) + 1;
		return new ParsedBlock(source.substring(start, Math.min(end, source.length())).strip(),
				headingPath, start, end, startLine, endLine);
	}

	private static int offset(int[] lineOffsets, SourceSpan span) {
		return lineOffsets[Math.min(span.getLineIndex(), lineOffsets.length - 1)] + span.getColumnIndex();
	}

	private static int[] lineOffsets(String value) {
		int lineCount = 1;
		for (int index = 0; index < value.length(); index++) if (value.charAt(index) == '\n') lineCount++;
		int[] offsets = new int[lineCount];
		int line = 1;
		for (int index = 0; index < value.length(); index++) {
			if (value.charAt(index) == '\n' && line < offsets.length) offsets[line++] = index + 1;
		}
		return offsets;
	}

	/** 根据标题级别维护面包屑路径，跳级标题用空槽保持层级位置稳定。 */
	private static void updateHeadingPath(List<String> headings, int level, String text) {
		while (headings.size() >= level) headings.removeLast();
		while (headings.size() < level - 1) headings.add("");
		headings.add(text);
	}

	private static String normalize(String value) {
		String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
		if (!normalized.isEmpty() && normalized.charAt(0) == '\uFEFF') {
			normalized = normalized.substring(1);
		}
		return Arrays.stream(normalized.split("\n", -1))
				.map(line -> line.replaceFirst("[ \\t]+$", ""))
				.reduce((left, right) -> left + "\n" + right)
				.orElse("");
	}
}
