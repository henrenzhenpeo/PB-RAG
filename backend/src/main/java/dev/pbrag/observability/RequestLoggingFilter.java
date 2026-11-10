package dev.pbrag.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为 API 请求建立稳定关联 ID，并记录不含请求正文与敏感参数的访问日志。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	public static final String ERROR_CODE_ATTRIBUTE = RequestLoggingFilter.class.getName() + ".errorCode";
	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
	private static final Pattern WORKSPACE_PATH = Pattern.compile(
			"^/api/workspaces/([0-9a-fA-F-]{36})(?:/.*)?$");

	/** 跳过非 API 请求，避免静态资源和健康检查污染业务日志。 */
	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !request.getRequestURI().startsWith("/api/");
	}

	/** 为请求建立 MDC 上下文并在完成后记录状态码、耗时和错误码。 */
	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		String requestId = requestId(request.getHeader(REQUEST_ID_HEADER));
		String workspaceId = workspaceId(request.getRequestURI());
		long startedAt = System.nanoTime();
		boolean failedBeforeResponse = false;

		try (MDC.MDCCloseable ignoredRequestId = MDC.putCloseable("requestId", requestId);
			 MDC.MDCCloseable ignoredWorkspaceId = putCloseable("workspaceId", workspaceId)) {
			response.setHeader(REQUEST_ID_HEADER, requestId);
			try {
				filterChain.doFilter(request, response);
			}
			catch (IOException | ServletException | RuntimeException exception) {
				failedBeforeResponse = true;
				throw exception;
			}
			finally {
				int status = failedBeforeResponse && response.getStatus() < 400
						? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
						: response.getStatus();
				long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
				String errorCode = stringAttribute(request, ERROR_CODE_ATTRIBUTE);
				logCompletion(request, status, durationMs, errorCode);
			}
		}
	}

	/** 复用合法的调用方请求 ID，否则生成新的追踪 ID。 */
	private static String requestId(String candidate) {
		if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}

	/** 从 API 路径提取 Workspace ID，供日志关联资源作用域。 */
	private static String workspaceId(String path) {
		Matcher matcher = WORKSPACE_PATH.matcher(path);
		if (!matcher.matches()) {
			return null;
		}
		try {
			return UUID.fromString(matcher.group(1)).toString();
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	/** 仅在值存在时写入 MDC，并返回可自动清理的句柄。 */
	private static MDC.MDCCloseable putCloseable(String key, String value) {
		return value == null ? null : MDC.putCloseable(key, value);
	}

	/** 将请求属性安全转换为字符串。 */
	private static String stringAttribute(HttpServletRequest request, String name) {
		Object value = request.getAttribute(name);
		return value instanceof String text ? text : "-";
	}

	/** 根据响应状态和异常信息输出统一的请求完成日志。 */
	private static void logCompletion(
			HttpServletRequest request, int status, long durationMs, String errorCode) {
		StringBuilder message = new StringBuilder("HTTP ")
				.append(request.getMethod())
				.append(' ')
				.append(request.getRequestURI())
				.append(" -> ")
				.append(status)
				.append(" (")
				.append(durationMs)
				.append(" ms)");
		if (request.getContentLengthLong() >= 0) {
			message.append(" bytes=").append(request.getContentLengthLong());
		}
		if (!"-".equals(errorCode)) {
			message.append(" error=").append(errorCode);
		}
		if (status >= 500) {
			log.error(message.toString());
		}
		else if (status >= 400) {
			log.warn(message.toString());
		}
		else {
			log.info(message.toString());
		}
	}
}
