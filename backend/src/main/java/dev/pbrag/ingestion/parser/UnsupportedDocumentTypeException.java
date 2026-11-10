package dev.pbrag.ingestion.parser;

/** 对象存储键没有匹配到受支持解析器时抛出的稳定失败类型。 */
public class UnsupportedDocumentTypeException extends RuntimeException {

	public UnsupportedDocumentTypeException(String storageKey) {
		super("No document parser supports storage object: " + storageKey);
	}
}
