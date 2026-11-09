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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthenticationFilter extends OncePerRequestFilter {
	private final UserAccountService accounts;
	public AuthenticationFilter(UserAccountService accounts) {
		this.accounts = accounts;
	}

	@Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().startsWith("/api/"); }

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

	private void deny(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write("{\"success\":false,\"data\":null,\"error\":{\"code\":\"" + code
				+ "\",\"message\":\"" + message + "\",\"fieldErrors\":[]},\"timestamp\":\""
				+ java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC) + "\"}");
	}
}
