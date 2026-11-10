package dev.pbrag.platform.api;

/** 业务输入不满足约束，对外映射为 HTTP 400。 */
public class InvalidRequestException extends RuntimeException {

	/** 创建携带客户端输入错误说明的业务异常。 */
	public InvalidRequestException(String message) {
		super(message);
	}
}
