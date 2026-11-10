package dev.pbrag.ingestion.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/** 普通文本 PDF 解析器；扫描件、复杂表格与版面 OCR 不在首期范围。 */
@Component
public class PdfDocumentParser implements DocumentParser {

	public static final String VERSION = "pdfbox-3-page-v1";

	/** 按页提取文本并保存页码；无可提取文本时明确提示需要 OCR，而不是生成空索引。 */
	@Override
	public boolean supports(String storageKey) {
		return storageKey != null && storageKey.toLowerCase().endsWith(".pdf");
	}

	@Override
	public ParsedDocument parse(InputStream input) throws IOException {
		byte[] bytes = input.readAllBytes();
		List<ParsedBlock> blocks = new ArrayList<>();
		int offset = 0;
		try (PDDocument document = Loader.loadPDF(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			stripper.setSortByPosition(true);
			for (int page = 1; page <= document.getNumberOfPages(); page++) {
				stripper.setStartPage(page);
				stripper.setEndPage(page);
				String text = normalize(stripper.getText(document));
				if (text.isBlank()) continue;
				int start = offset;
				int end = start + text.length();
				int lineCount = Math.max(1, text.split("\n", -1).length);
				blocks.add(new ParsedBlock(text, "Page " + page, start, end, 1, lineCount, page));
				offset = end + 2;
			}
		}
		if (blocks.isEmpty()) {
			throw new IllegalArgumentException("PDF contains no extractable text; OCR may be required");
		}
		return new ParsedDocument(VERSION, blocks);
	}

	private static String normalize(String value) {
		return value.replace("\r\n", "\n").replace('\r', '\n')
				.replaceAll("[ \\t]+\n", "\n").strip();
	}
}
