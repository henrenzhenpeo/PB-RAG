package dev.pbrag.identity.api;

import dev.pbrag.identity.service.UserAccountService;
import dev.pbrag.identity.service.UserAccountService.User;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import dev.pbrag.platform.api.InvalidRequestException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 账号注册、登录、退出及个人资料 HTTP 接口，并负责安全会话 Cookie 的收发。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
	public static final String COOKIE = "pbrag_session";
	public static final String USER_ATTRIBUTE = AuthController.class.getName() + ".user";
	private final UserAccountService accounts;
	/** 注入账号认证应用服务。 */
	public AuthController(UserAccountService accounts) { this.accounts = accounts; }

	/** 注册账号、创建会话并向浏览器写入安全 Cookie。 */
	@PostMapping("/register")
	public ResponseEntity<ApiResponse<User>> register(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
		var session = accounts.register(input.email(), input.displayName(), input.password());
		setCookie(request, response, session.token(), Duration.ofDays(7));
		return ApiResponses.ok(session.user());
	}

	/** 校验邮箱密码、创建新会话并向浏览器写入安全 Cookie。 */
	@PostMapping("/login")
	public ResponseEntity<ApiResponse<User>> login(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
		var session = accounts.login(input.email(), input.password());
		setCookie(request, response, session.token(), Duration.ofDays(7));
		return ApiResponses.ok(session.user());
	}

	/** 删除服务端会话并让浏览器立即清除会话 Cookie。 */
	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request, HttpServletResponse response) {
		accounts.logout(token(request));
		setCookie(request, response, "", Duration.ZERO);
		return ApiResponses.ok(true);
	}

	/** 返回认证过滤器写入请求属性的当前登录用户。 */
	@GetMapping("/me")
	public ResponseEntity<ApiResponse<User>> me(HttpServletRequest request) { return ApiResponses.ok(requiredUser(request)); }

	/** 更新当前用户的显示名称。 */
	@PutMapping("/me")
	public ResponseEntity<ApiResponse<User>> update(@RequestBody UpdateProfile input, HttpServletRequest request) {
		return ApiResponses.ok(accounts.update(requiredUser(request).id(), input.displayName()));
	}

	/** 获取当前登录用户；请求没有认证上下文时抛出统一业务异常。 */
	public static User requiredUser(HttpServletRequest request) {
		User user = (User) request.getAttribute(USER_ATTRIBUTE);
		if (user == null) throw new InvalidRequestException("请先登录");
		return user;
	}

	/** 从请求 Cookie 中提取原始会话令牌，没有对应 Cookie 时返回 null。 */
	public static String token(HttpServletRequest request) {
		if (request.getCookies() == null) return null;
		for (Cookie cookie : request.getCookies()) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
		return null;
	}

	/** Cookie 禁止脚本读取；HTTPS 请求自动启用 Secure，SameSite=Lax 降低跨站携带风险。 */
	private static void setCookie(HttpServletRequest request, HttpServletResponse response, String value, Duration age) {
		boolean secure = request.isSecure();
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure)
				.sameSite("Lax").path("/").maxAge(age).build().toString());
	}

	public record Credentials(String email, String displayName, String password) { }
	public record UpdateProfile(String displayName) { }
}
