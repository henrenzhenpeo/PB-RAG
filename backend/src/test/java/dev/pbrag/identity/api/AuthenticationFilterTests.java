package dev.pbrag.identity.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.pbrag.identity.service.UserAccountService;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthenticationFilterTests {
	private final UserAccountService accounts = mock(UserAccountService.class);
	private final AuthenticationFilter filter = new AuthenticationFilter(accounts);

	@Test void rejectsUnauthenticatedWorkspaceRequest() throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/workspaces");
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, mock(FilterChain.class));
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("AUTH_REQUIRED");
	}

	@Test void rejectsMutationWithoutCustomHeader() throws Exception {
		var request = new MockHttpServletRequest("POST", "/api/auth/register");
		var response = new MockHttpServletResponse();
		filter.doFilter(request, response, mock(FilterChain.class));
		assertThat(response.getStatus()).isEqualTo(403);
		verifyNoInteractions(accounts);
	}

	@Test void passesAuthenticatedRequestToController() throws Exception {
		var request = new MockHttpServletRequest("GET", "/api/workspaces");
		request.setCookies(new jakarta.servlet.http.Cookie(AuthController.COOKIE, "token"));
		var user = new UserAccountService.User(java.util.UUID.randomUUID(), "a@example.com", "A");
		when(accounts.current("token")).thenReturn(user);
		var chain = mock(FilterChain.class);
		filter.doFilter(request, new MockHttpServletResponse(), chain);
		assertThat(request.getAttribute(AuthController.USER_ATTRIBUTE)).isEqualTo(user);
		org.mockito.Mockito.verify(chain).doFilter(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}
}
