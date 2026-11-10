package dev.pbrag.ingestion.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MarkdownDocumentParserTests {

	private final MarkdownDocumentParser parser = new MarkdownDocumentParser();

	@Test
	void preservesHeadingPathAndSourceLines() throws Exception {
		String markdown = """
				# API

				概述。

				## 认证

				使用令牌。
				""";

		ParsedDocument document = parser.parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

		assertThat(document.parserVersion()).isEqualTo(MarkdownDocumentParser.VERSION);
		assertThat(document.blocks()).extracting(ParsedBlock::headingPath)
				.contains("API", "API > 认证");
		ParsedBlock authentication = document.blocks().getLast();
		assertThat(authentication.content()).contains("使用令牌");
		assertThat(authentication.startLine()).isEqualTo(7);
		assertThat(authentication.endOffset()).isGreaterThan(authentication.startOffset());
	}

	@Test
	void normalizesBomAndWindowsLineEndings() throws Exception {
		String markdown = "\uFEFF# 标题\r\n\r\n正文\r\n";

		ParsedDocument document = parser.parse(new ByteArrayInputStream(markdown.getBytes(StandardCharsets.UTF_8)));

		assertThat(document.blocks().getFirst().content()).isEqualTo("# 标题");
		assertThat(document.blocks().getLast().content()).isEqualTo("正文");
	}
}
