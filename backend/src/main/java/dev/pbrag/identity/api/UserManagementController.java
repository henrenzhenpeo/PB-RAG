package dev.pbrag.identity.api;

import dev.pbrag.identity.service.UserAccountService;
import dev.pbrag.identity.service.UserAccountService.User;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 已登录用户可管理 app_users 账号资料的接口。 */
@RestController
@RequestMapping("/api/users")
public class UserManagementController {
	private final UserAccountService accounts;

	public UserManagementController(UserAccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<User>>> list() {
		return ApiResponses.ok(accounts.list());
	}

	@PostMapping
	public ResponseEntity<ApiResponse<User>> create(@Valid @RequestBody CreateUserRequest request) {
		User user = accounts.create(request.email(), request.displayName(), request.password());
		return ApiResponses.created(URI.create("/api/users/" + user.id()), user);
	}

	@PutMapping("/{id}")
	public ResponseEntity<ApiResponse<User>> update(
			@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest request) {
		return ApiResponses.ok(accounts.updateAccount(
				id, request.email(), request.displayName(), request.password()));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<ApiResponse<Boolean>> delete(@PathVariable UUID id) {
		accounts.delete(id);
		return ApiResponses.ok(true);
	}

	public record CreateUserRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 120) String displayName,
			@NotBlank @Size(min = 6, max = 128) String password) { }

	public record UpdateUserRequest(
			@NotBlank @Email @Size(max = 254) String email,
			@NotBlank @Size(max = 120) String displayName,
			@Size(min = 6, max = 128) String password) { }
}
