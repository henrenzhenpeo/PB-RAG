package dev.pbrag.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 服务端会话持久化对象；数据库只保存令牌摘要，绝不保存浏览器持有的原始令牌。 */
@TableName("user_sessions")
public class UserSession {
	@TableId(type = IdType.INPUT)
	private String tokenHash;
	private UUID userId;
	private OffsetDateTime expiresAt;
	private OffsetDateTime createdAt;

	protected UserSession() { /* MyBatis 通过无参构造器恢复持久化对象。 */ }

	public UserSession(String tokenHash, UUID userId, OffsetDateTime expiresAt) {
		this.tokenHash = tokenHash;
		this.userId = userId;
		this.expiresAt = expiresAt;
	}

	public String getTokenHash() { return tokenHash; }
	public UUID getUserId() { return userId; }
	public OffsetDateTime getExpiresAt() { return expiresAt; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
