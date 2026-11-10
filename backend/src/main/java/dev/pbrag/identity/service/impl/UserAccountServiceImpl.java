package dev.pbrag.identity.service.impl;

import dev.pbrag.identity.domain.UserAccount;
import dev.pbrag.identity.domain.UserSession;
import dev.pbrag.identity.mapper.UserAccountMapper;
import dev.pbrag.identity.mapper.UserSessionMapper;
import dev.pbrag.identity.service.UserAccountService;
import dev.pbrag.platform.api.ConflictException;
import dev.pbrag.platform.api.InvalidRequestException;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 账号认证业务实现。
 *
 * <p>事务、输入规则和密码/令牌安全策略集中在服务层；Mapper 只负责持久化，避免安全
 * 规则散落在 Controller 或 SQL 中。</p>
 */
@Service
public class UserAccountServiceImpl implements UserAccountService {
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final int PASSWORD_ITERATIONS = 210_000;
	private static final int PASSWORD_KEY_LENGTH = 256;
	private static final int SESSION_TOKEN_BYTES = 32;
	private static final int SESSION_VALID_DAYS = 7;

	private final UserAccountMapper userAccountMapper;
	private final UserSessionMapper userSessionMapper;

	/** 注入账号与会话 Mapper，认证流程不直接依赖 JDBC。 */
	public UserAccountServiceImpl(UserAccountMapper userAccountMapper, UserSessionMapper userSessionMapper) {
		this.userAccountMapper = userAccountMapper;
		this.userSessionMapper = userSessionMapper;
	}

	/** 注册账号与首个会话必须原子提交，避免出现注册成功却无法登录的半完成状态。 */
	/** 根据令牌摘要读取有效会话对应用户；无效或过期令牌返回 null。 */
	@Override
	@Transactional
	public Session register(String email, String displayName, String password) {
		String normalizedEmail = normalizeEmail(email);
		String normalizedName = normalizeDisplayName(displayName);
		validatePassword(password);
		UserAccount account = new UserAccount(UUID.randomUUID(), normalizedEmail, normalizedName, hashPassword(password));
		try {
			userAccountMapper.insert(account);
		}
		catch (DuplicateKeyException exception) {
			throw new ConflictException("邮箱已注册", exception);
		}
		return createSession(toUser(account));
	}

	/** 登录失败统一返回相同信息，避免泄露某个邮箱是否已经注册。 */
	/** 校验展示名称后更新账号资料，并返回更新后的用户视图。 */
	@Override
	@Transactional
	public Session login(String email, String password) {
		UserAccount account = userAccountMapper.selectByEmail(normalizeEmail(email));
		if (account == null || !verifyPassword(password, account.getPasswordHash())) {
			throw new InvalidRequestException("邮箱或密码错误");
		}
		return createSession(toUser(account));
	}

	/** 删除令牌摘要对应的服务端会话，使该 Cookie 立即失效。 */
	@Override
	@Transactional(readOnly = true)
	public User current(String token) {
		if (token == null || token.isBlank()) {
			return null;
		}
		UserAccount account = userAccountMapper.selectByActiveSession(sha256(token));
		return account == null ? null : toUser(account);
	}

	@Override
	@Transactional
	public User update(UUID id, String displayName) {
		String normalizedName = normalizeDisplayName(displayName);
		if (userAccountMapper.updateDisplayName(id, normalizedName) == 0) {
			throw new ResourceNotFoundException("用户不存在");
		}
		return toUser(userAccountMapper.selectById(id));
	}

	@Override
	@Transactional
	public void logout(String token) {
		if (token != null && !token.isBlank()) {
			userSessionMapper.deleteById(sha256(token));
		}
	}

	/** 原始令牌只返回给浏览器，持久层仅接触不可逆 SHA-256 摘要。 */
	private Session createSession(User user) {
		byte[] tokenBytes = new byte[SESSION_TOKEN_BYTES];
		RANDOM.nextBytes(tokenBytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
		OffsetDateTime expiresAt = OffsetDateTime.now(ZoneOffset.UTC).plusDays(SESSION_VALID_DAYS);
		userSessionMapper.insert(new UserSession(sha256(token), user.id(), expiresAt));
		return new Session(token, user);
	}

	/** 将持久化账号实体转换为不包含密码摘要的对外用户视图。 */
	private static User toUser(UserAccount account) {
		return new User(account.getId(), account.getEmail(), account.getDisplayName());
	}

	/** 校验邮箱格式并统一为小写，保证登录和唯一键语义一致。 */
	private static String normalizeEmail(String email) {
		if (email == null || email.length() > 254
				|| !email.strip().matches("(?i)^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")) {
			throw new InvalidRequestException("邮箱格式不正确");
		}
		return email.strip().toLowerCase(Locale.ROOT);
	}

	/** 去除展示名称首尾空白并限制长度。 */
	private static String normalizeDisplayName(String displayName) {
		if (displayName == null || displayName.isBlank() || displayName.strip().length() > 120) {
			throw new InvalidRequestException("名称长度需为 1 到 120 个字符");
		}
		return displayName.strip();
	}

	/** 校验明文密码长度，拒绝明显不合规输入。 */
	private static void validatePassword(String password) {
		if (password == null || password.length() < 6 || password.length() > 128) {
			throw new InvalidRequestException("密码长度需为 6 到 128 个字符");
		}
	}

	/** PBKDF2 每个密码使用独立随机盐；迭代次数随散列一起保存，便于以后平滑升级。 */
	private static String hashPassword(String password) {
		try {
			byte[] salt = new byte[16];
			RANDOM.nextBytes(salt);
			byte[] hash = derivePassword(password, salt, PASSWORD_ITERATIONS);
			return "pbkdf2:" + PASSWORD_ITERATIONS + ":" + Base64.getEncoder().encodeToString(salt)
					+ ":" + Base64.getEncoder().encodeToString(hash);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Password hashing unavailable", exception);
		}
	}

	/** 使用恒定时间比较验证散列，降低基于比较耗时推断密码内容的风险。 */
	private static boolean verifyPassword(String password, String stored) {
		try {
			if (password == null || stored == null) {
				return false;
			}
			String[] parts = stored.split(":");
			if (parts.length != 4 || !"pbkdf2".equals(parts[0])) {
				return false;
			}
			byte[] salt = Base64.getDecoder().decode(parts[2]);
			byte[] actual = derivePassword(password, salt, Integer.parseInt(parts[1]));
			return MessageDigest.isEqual(actual, Base64.getDecoder().decode(parts[3]));
		}
		catch (Exception exception) {
			return false;
		}
	}

	/** 使用指定盐值和迭代次数执行 PBKDF2 密钥派生。 */
	private static byte[] derivePassword(String password, byte[] salt, int iterations) throws Exception {
		PBEKeySpec specification = new PBEKeySpec(password.toCharArray(), salt, iterations, PASSWORD_KEY_LENGTH);
		try {
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(specification).getEncoded();
		}
		finally {
			specification.clearPassword();
		}
	}

	/** 计算会话令牌 SHA-256 摘要，数据库不保存可直接使用的令牌。 */
	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Session token hashing unavailable", exception);
		}
	}
}
