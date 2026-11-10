package dev.pbrag.platform.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ResponseEntity 的统一构造入口，避免各控制器重复拼装响应结构。 */
public final class ApiResponses {

	/** 禁止实例化纯静态响应工厂。 */
	private ApiResponses() {
	}

	/** 返回 HTTP 200 的统一成功响应。 */
	public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	/** 返回带 Location 头的 HTTP 201 创建响应。 */
	public static <T> ResponseEntity<ApiResponse<T>> created(URI location, T data) {
		return ResponseEntity.created(location).body(ApiResponse.success(data));
	}

	/** 返回不包含字段错误的统一失败响应。 */
	public static ResponseEntity<ApiResponse<Void>> error(
			HttpStatus status,
			String code,
			String message) {
		return error(status, code, message, List.of());
	}

	/** 返回包含字段校验详情的统一失败响应。 */
	public static ResponseEntity<ApiResponse<Void>> error(
			HttpStatus status,
			String code,
			String message,
			List<ApiFieldError> fieldErrors) {
		return ResponseEntity.status(status)
				.body(ApiResponse.failure(new ApiError(code, message, fieldErrors)));
	}
}
