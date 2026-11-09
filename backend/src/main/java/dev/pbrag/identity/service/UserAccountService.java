package dev.pbrag.identity.service;

import dev.pbrag.platform.api.ConflictException;
import dev.pbrag.platform.api.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {
	private static final SecureRandom RANDOM = new SecureRandom();
	private final JdbcTemplate jdbc;

	public UserAccountService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

	public record User(UUID id, String email, String displayName) { }
	public record Session(String token, User user) { }

	@Transactional
	public Session register(String email, String displayName, String password) {
		String normalized = normalize(email);
		validateName(displayName);
		validatePassword(password);
		UUID id = UUID.randomUUID();
		try {
			jdbc.update("INSERT INTO app_users (id,email,display_name,password_hash) VALUES (?,?,?,?)",
					id, normalized, displayName.strip(), hashPassword(password));
		} catch (DuplicateKeyException exception) {
			throw new ConflictException("邮箱已注册", exception);
		}
		return createSession(new User(id, normalized, displayName.strip()));
	}

	@Transactional
	public Session login(String email, String password) {
		String normalized = normalize(email);
		var rows = jdbc.query("SELECT id,email,display_name,password_hash FROM app_users WHERE email=?",
				(rs, row) -> new StoredUser(new User(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")), rs.getString("password_hash")), normalized);
		if (rows.isEmpty() || !verifyPassword(password, rows.getFirst().passwordHash())) {
			throw new InvalidRequestException("邮箱或密码错误");
		}
		return createSession(rows.getFirst().user());
	}

	public User current(String token) {
		if (token == null || token.isBlank()) return null;
		var rows = jdbc.query("SELECT u.id,u.email,u.display_name FROM user_sessions s JOIN app_users u ON u.id=s.user_id WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP",
				(rs, row) -> new User(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")), sha256(token));
		return rows.isEmpty() ? null : rows.getFirst();
	}

	@Transactional
	public User update(UUID id, String displayName) {
		validateName(displayName);
		jdbc.update("UPDATE app_users SET display_name=?,updated_at=CURRENT_TIMESTAMP WHERE id=?", displayName.strip(), id);
		return jdbc.queryForObject("SELECT id,email,display_name FROM app_users WHERE id=?",
				(rs, row) -> new User(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name")), id);
	}

	public void logout(String token) {
		if (token != null && !token.isBlank()) jdbc.update("DELETE FROM user_sessions WHERE token_hash=?", sha256(token));
	}

	private Session createSession(User user) {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		jdbc.update("INSERT INTO user_sessions (token_hash,user_id,expires_at) VALUES (?,?,?)",
				sha256(token), user.id(), OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
		return new Session(token, user);
	}

	private static String normalize(String email) {
		if (email == null || !email.strip().matches("(?i)^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$") || email.length() > 254)
			throw new InvalidRequestException("邮箱格式不正确");
		return email.strip().toLowerCase(java.util.Locale.ROOT);
	}
	private static void validateName(String name) {
		if (name == null || name.isBlank() || name.strip().length() > 120) throw new InvalidRequestException("名称长度需为 1 到 120 个字符");
	}
	private static void validatePassword(String password) {
		if (password == null || password.length() < 6 || password.length() > 128) throw new InvalidRequestException("密码长度需为 6 到 128 个字符");
	}
	private static String hashPassword(String password) {
		try {
			byte[] salt = new byte[16]; RANDOM.nextBytes(salt);
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 210_000, 256);
			byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			spec.clearPassword();
			return "pbkdf2:210000:" + Base64.getEncoder().encodeToString(salt) + ":" + Base64.getEncoder().encodeToString(hash);
		} catch (Exception exception) { throw new IllegalStateException("Password hashing unavailable", exception); }
	}
	private static boolean verifyPassword(String password, String stored) {
		try {
			if (password == null || stored == null) return false;
			String[] parts = stored.split(":");
			if (parts.length != 4 || !parts[0].equals("pbkdf2")) return false;
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), Base64.getDecoder().decode(parts[2]), Integer.parseInt(parts[1]), 256);
			byte[] actual = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
			spec.clearPassword();
			return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[3]));
		} catch (Exception exception) { return false; }
	}
	private static String sha256(String value) {
		try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
		catch (Exception exception) { throw new IllegalStateException(exception); }
	}
	private record StoredUser(User user, String passwordHash) { }
}
