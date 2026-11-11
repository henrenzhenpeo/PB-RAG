package dev.pbrag.retrieval.rerank;

/** 表示供应商返回了不能安全解释的重排响应，不允许静默降级。 */
public class RerankProtocolException extends RuntimeException {
	public RerankProtocolException(String message) {
		super(message);
	}

	public RerankProtocolException(String message, Throwable cause) {
		super(message, cause);
	}
}
