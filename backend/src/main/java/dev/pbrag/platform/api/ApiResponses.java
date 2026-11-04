package dev.pbrag.platform.api;

import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** ResponseEntity 的统一构造入口，避免各控制器重复拼装响应结构。 */
public final class ApiResponses {

	private ApiResponses() {
	}

	public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
		return ResponseEntity.ok(ApiResponse.success(data));
	}

	public static <T> ResponseEntity<ApiResponse<T>> created(URI location, T data) {
		return ResponseEntity.created(location).body(ApiResponse.success(data));
	}

	public static ResponseEntity<ApiResponse<Void>> error(
			HttpStatus status,
			String code,
			String message) {
		return error(status, code, message, List.of());
	}

	public static ResponseEntity<ApiResponse<Void>> error(
			HttpStatus status,
			String code,
			String message,
			List<ApiFieldError> fieldErrors) {
		return ResponseEntity.status(status)
				.body(ApiResponse.failure(new ApiError(code, message, fieldErrors)));
	}
}
