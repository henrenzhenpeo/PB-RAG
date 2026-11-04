package dev.pbrag.platform.api;

/** 单个请求字段的校验错误。 */
public record ApiFieldError(String field, String message) {
}
