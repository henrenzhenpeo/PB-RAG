package dev.pbrag.platform.api;

import java.util.List;

/**
 * 稳定的客户端错误契约。code 用于前端分支判断，message 用于展示，fieldErrors 用于表单定位。
 */
public record ApiError(String code, String message, List<ApiFieldError> fieldErrors) {

	public ApiError {
		fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
	}
}
