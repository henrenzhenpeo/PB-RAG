package dev.pbrag.retrieval.rerank;

/** 允许 Production RAG 降级到 RRF 的远程重排瞬时故障。 */
public class RerankTransientException extends RuntimeException {
	public RerankTransientException(String message, Throwable cause) { super(message, cause); }
}
