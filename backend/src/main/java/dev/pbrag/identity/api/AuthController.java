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

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	public static final String COOKIE = "pbrag_session";
	public static final String USER_ATTRIBUTE = AuthController.class.getName() + ".user";
	private final UserAccountService accounts;
	public AuthController(UserAccountService accounts) { this.accounts = accounts; }

	@PostMapping("/register")
	public ResponseEntity<ApiResponse<User>> register(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
		var session = accounts.register(input.email(), input.displayName(), input.password());
		setCookie(request, response, session.token(), Duration.ofDays(7));
		return ApiResponses.ok(session.user());
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<User>> login(@RequestBody Credentials input, HttpServletRequest request, HttpServletResponse response) {
		var session = accounts.login(input.email(), input.password());
		setCookie(request, response, session.token(), Duration.ofDays(7));
		return ApiResponses.ok(session.user());
	}

	@PostMapping("/logout")
	public ResponseEntity<ApiResponse<Boolean>> logout(HttpServletRequest request, HttpServletResponse response) {
		accounts.logout(token(request));
		setCookie(request, response, "", Duration.ZERO);
		return ApiResponses.ok(true);
	}

	@GetMapping("/me")
	public ResponseEntity<ApiResponse<User>> me(HttpServletRequest request) { return ApiResponses.ok(requiredUser(request)); }

	@PutMapping("/me")
	public ResponseEntity<ApiResponse<User>> update(@RequestBody UpdateProfile input, HttpServletRequest request) {
		return ApiResponses.ok(accounts.update(requiredUser(request).id(), input.displayName()));
	}

	public static User requiredUser(HttpServletRequest request) {
		User user = (User) request.getAttribute(USER_ATTRIBUTE);
		if (user == null) throw new InvalidRequestException("请先登录");
		return user;
	}

	public static String token(HttpServletRequest request) {
		if (request.getCookies() == null) return null;
		for (Cookie cookie : request.getCookies()) if (COOKIE.equals(cookie.getName())) return cookie.getValue();
		return null;
	}

	private static void setCookie(HttpServletRequest request, HttpServletResponse response, String value, Duration age) {
		boolean secure = request.isSecure();
		response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, value).httpOnly(true).secure(secure)
				.sameSite("Lax").path("/").maxAge(age).build().toString());
	}

	public record Credentials(String email, String displayName, String password) { }
	public record UpdateProfile(String displayName) { }
}
