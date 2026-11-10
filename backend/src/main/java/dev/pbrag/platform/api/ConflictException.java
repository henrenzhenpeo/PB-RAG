package dev.pbrag.platform.api;

/** 资源唯一性或当前状态冲突，对外映射为 HTTP 409。 */
public class ConflictException extends RuntimeException {

	/** 将底层唯一键等冲突包装为可返回 409 的业务异常。 */
	public ConflictException(String message, Throwable cause) {
		super(message, cause);
	}
}
