package dev.pbrag.identity.api;

import dev.pbrag.identity.service.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * API 认证与基础跨站写请求防护过滤器。
 * 登录、注册允许匿名访问；其他 API 必须携带有效会话。写请求额外要求自定义请求头，
 * 该规则是确定性安全边界，不依赖前端页面或模型行为。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationFilter extends OncePerRequestFilter {
	private final UserAccountService accounts;
	/** 注入账号服务，用于根据 Cookie 令牌查询有效会话。 */
	public AuthenticationFilter(UserAccountService accounts) {
		this.accounts = accounts;
	}

	/** 只过滤 `/api/` 请求，静态资源和前端路由不进入认证流程。 */
	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/"); }

	/** 先校验写请求来源标记，再解析会话，避免未授权请求进入任何业务 Controller。 */
	@Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
		String path = request.getRequestURI();
		String method = request.getMethod();
		if (!"GET".equals(method) && !"HEAD".equals(method) && !"OPTIONS".equals(method)) {
			if (!"1".equals(request.getHeader("X-PBRAG-Request"))) {
				deny(response, 403, "INVALID_ORIGIN", "请求来源不受信任"); return;
			}
		}
		if (path.equals("/api/auth/login") || path.equals("/api/auth/register")) { chain.doFilter(request, response); return; }
		var user = accounts.current(AuthController.token(request));
		if (user == null) { deny(response, 401, "AUTH_REQUIRED", "请先登录"); return; }
		request.setAttribute(AuthController.USER_ATTRIBUTE, user);
		chain.doFilter(request, response);
	}

	/** 直接写出统一错误结构，阻止未授权请求继续进入 Controller。 */
	private void deny(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code
				+ "\",\"message\":\"" + message + "\",\"fieldErrors\":[]},\"timestamp\":\""
				+ java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) + "\"}");
	}
}
