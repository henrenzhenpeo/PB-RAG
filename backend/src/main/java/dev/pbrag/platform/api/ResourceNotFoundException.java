package dev.pbrag.platform.api;

/** 资源不存在或不属于指定 Workspace 范围，对外统一映射为 HTTP 404。 */
public class ResourceNotFoundException extends RuntimeException {

	/** 创建携带资源不存在说明的业务异常。 */
	public ResourceNotFoundException(String message) {
		super(message);
	}
}
