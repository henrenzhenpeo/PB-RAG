package dev.pbrag.ingestion.parser;

import java.util.List;
import org.springframework.stereotype.Component;

/** 根据对象存储键选择解析器；未匹配时返回结构化的不支持类型错误。 */
@Component
public class DocumentParserRegistry {

	private final List<DocumentParser> parsers;

	/** 注入当前应用可用的全部文档解析器。 */
	public DocumentParserRegistry(List<DocumentParser> parsers) {
		this.parsers = List.copyOf(parsers);
	}

	/** 按文件类型选择解析器，不支持时抛出明确业务异常。 */
	public DocumentParser requiredParser(String storageKey) {
		return parsers.stream()
				.filter(parser -> parser.supports(storageKey))
				.findFirst()
				.orElseThrow(() -> new UnsupportedDocumentTypeException(storageKey));
	}
}
