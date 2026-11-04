package dev.pbrag.platform.api;

import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将领域和协议异常转换为统一错误信封，不向客户端暴露数据库及调用栈信息。 */
@RestControllerAdvice
public class ApiExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ResourceNotFoundException.class)
	ResponseEntity<ApiResponse<Void>> handleNotFound(ResourceNotFoundException exception) {
		return ApiResponses.error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage());
	}

	@ExceptionHandler(ConflictException.class)
	ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException exception) {
		return ApiResponses.error(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", exception.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception) {
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
	ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception) {
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
	ResponseEntity<ApiResponse<Void>> handleUnreadableMessage() {
		return ApiResponses.error(
				HttpStatus.BAD_REQUEST,
				"MALFORMED_REQUEST",
				"Request body is missing or malformed");
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
		log.error("Unhandled API exception", exception);
		return ApiResponses.error(
				HttpStatus.INTERNAL_SERVER_ERROR,
				"INTERNAL_ERROR",
				"An unexpected error occurred");
	}
}
