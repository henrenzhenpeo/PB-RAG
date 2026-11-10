package dev.pbrag.ingestion.service;

/** 模型输出向量维度与数据库列配置不一致，必须显式失败以避免损坏索引。 */
public class EmbeddingDimensionMismatchException extends RuntimeException {

	public EmbeddingDimensionMismatchException(int expected, int actual) {
		super("Embedding dimension mismatch: database expects %d but model returned %d".formatted(expected, actual));
	}
}
