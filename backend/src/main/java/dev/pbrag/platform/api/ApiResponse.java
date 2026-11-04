package dev.pbrag.platform.api;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * 业务 HTTP 接口的统一响应信封。HTTP 状态码仍表达协议语义，success 方便客户端统一解包。
 */
public record ApiResponse<T>(
		boolean success,
		T data,
		ApiError error,
		OffsetDateTime timestamp) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null, OffsetDateTime.now(ZoneOffset.UTC));
	}

	public static <T> ApiResponse<T> failure(ApiError error) {
		return new ApiResponse<>(false, null, error, OffsetDateTime.now(ZoneOffset.UTC));
	}
}
