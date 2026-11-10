package dev.pbrag.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.pbrag.identity.domain.UserAccount;
import dev.pbrag.identity.domain.UserSession;
import dev.pbrag.identity.mapper.UserAccountMapper;
import dev.pbrag.identity.mapper.UserSessionMapper;
import dev.pbrag.identity.service.impl.UserAccountServiceImpl;
import dev.pbrag.platform.api.InvalidRequestException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UserAccountServiceTests {
	private final UserAccountMapper userAccountMapper = mock(UserAccountMapper.class);
	private final UserSessionMapper userSessionMapper = mock(UserSessionMapper.class);
	private final UserAccountService service = new UserAccountServiceImpl(userAccountMapper, userSessionMapper);

	@Test
	void registersNormalizedAccountAndStoresOnlySessionTokenHash() {
		UserAccountService.Session session = service.register(" Alice@Example.com ", " Alice ", "secret12");

		ArgumentCaptor<UserAccount> account = ArgumentCaptor.forClass(UserAccount.class);
		verify(userAccountMapper).insert(account.capture());
		assertThat(account.getValue().getEmail()).isEqualTo("alice@example.com");
		assertThat(account.getValue().getDisplayName()).isEqualTo("Alice");
		assertThat(account.getValue().getPasswordHash()).startsWith("pbkdf2:210000:").doesNotContain("secret12");

		ArgumentCaptor<UserSession> storedSession = ArgumentCaptor.forClass(UserSession.class);
		verify(userSessionMapper).insert(storedSession.capture());
		assertThat(storedSession.getValue().getTokenHash()).hasSize(64).doesNotContain(session.token());
		assertThat(storedSession.getValue().getUserId()).isEqualTo(session.user().id());
	}

	@Test
	void rejectsInvalidInputBeforeWritingAnything() {
		assertThatThrownBy(() -> service.register("invalid", "Alice", "secret12"))
				.isInstanceOf(InvalidRequestException.class);
		verifyNoInteractions(userAccountMapper, userSessionMapper);
	}

	@Test
	void resolvesCurrentUserThroughHashedSessionToken() {
		UUID userId = UUID.randomUUID();
		when(userAccountMapper.selectByActiveSession(any())).thenReturn(
				new UserAccount(userId, "alice@example.com", "Alice", "not-exposed"));

		UserAccountService.User user = service.current("browser-token");

		assertThat(user.id()).isEqualTo(userId);
		ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
		verify(userAccountMapper).selectByActiveSession(tokenHash.capture());
		assertThat(tokenHash.getValue()).hasSize(64).isNotEqualTo("browser-token");
	}

	@Test
	void logoutDeletesSessionByTokenHash() {
		service.logout("browser-token");

		ArgumentCaptor<String> tokenHash = ArgumentCaptor.forClass(String.class);
		verify(userSessionMapper).deleteById(tokenHash.capture());
		assertThat(tokenHash.getValue()).hasSize(64).isNotEqualTo("browser-token");
	}
}
