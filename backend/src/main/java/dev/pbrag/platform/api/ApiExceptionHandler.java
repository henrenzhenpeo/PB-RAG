package dev.pbrag.platform.api;

import dev.pbrag.observability.RequestLoggingFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** 将领域和协议异常转换为统一错误信封，不向客户端暴露数据库及调用栈信息。 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	/**
	 * SSE 客户端停止、切换页面或关闭连接后，Servlet 容器可能在下一次写入时报告连接不可用。
	 * 此时响应通常已经提交，不能再写统一 JSON 错误信封，否则会产生二次转换异常。
	 */
	@ExceptionHandler(AsyncRequestNotUsableException.class)
	void handleDisconnectedClient(AsyncRequestNotUsableException exception, HttpServletRequest request) {
		markError(request, "CLIENT_DISCONNECTED");
		log.debug("Client disconnected while an asynchronous response was being written: {}",
				exception.getMessage());
	}

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNotFound(
			ResourceNotFoundException exception, HttpServletRequest request) {
		markError(request, "RESOURCE_NOT_FOUND");
		return ApiResponses.error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException exception, HttpServletRequest request) {
		markError(request, "RESOURCE_CONFLICT");
		return ApiResponses.error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleValidation(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		markError(request, "VALIDATION_FAILED");
		List<ApiFieldError> errors = exception.getBindingResult().getFieldErrors().stream()
				.map(error -> new ApiFieldError(error.getField(), error.getDefaultMessage()))
				.toList();
		return ApiResponses.error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"Request validation failed",
				errors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ApiResponse<Void>> handleConstraintViolation(
			ConstraintViolationException exception, HttpServletRequest request) {
		markError(request, "VALIDATION_FAILED");
		List<ApiFieldError> errors = exception.getConstraintViolations().stream()
				.map(violation -> new ApiFieldError(
						violation.getPropertyPath().toString(), violation.getMessage()))
				.toList();
		return ApiResponses.error(
				HttpStatus.BAD_REQUEST,
				"VALIDATION_FAILED",
				"Request validation failed",
				errors);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<ApiResponse<Void>> handleUnreadableMessage(HttpServletRequest request) {
		markError(request, "MALFORMED_REQUEST");
		return ApiResponses.error(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"Request body is missing or malformed");
	}

	@ExceptionHandler(InvalidRequestException.class)
	ResponseEntity<ApiResponse<Void>> handleInvalidRequest(
			InvalidRequestException exception, HttpServletRequest request) {
		markError(request, "INVALID_REQUEST");
		return ApiResponses.error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(HttpServletRequest request) {
		markError(request, "FILE_TOO_LARGE");
		return ApiResponses.error(HttpStatus.BAD_REQUEST, "FILE_TOO_LARGE", "Uploaded file exceeds the size limit");
	}

	@ExceptionHandler(MissingServletRequestPartException.class)
	ResponseEntity<ApiResponse<Void>> handleMissingRequestPart(
			MissingServletRequestPartException exception, HttpServletRequest request) {
		markError(request, "MISSING_REQUEST_PART");
		return ApiResponses.error(
				HttpStatus.BAD_REQUEST,
				"MISSING_REQUEST_PART",
				"Required multipart field is missing: " + exception.getRequestPartName());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
		markError(request, "INTERNAL_ERROR");
		log.error("Unhandled API exception", exception);
		return ApiResponses.error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred");
	}

	private static void markError(HttpServletRequest request, String errorCode) {
		request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, errorCode);
	}
}
