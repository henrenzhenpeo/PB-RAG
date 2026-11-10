package dev.pbrag.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 用户账号持久化对象。密码字段只保存带随机盐的 PBKDF2 结果。 */
@TableName("app_users")
public class UserAccount {
	@TableId(type = IdType.INPUT)
	private UUID id;
	private String email;
	private String displayName;
	private String passwordHash;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	protected UserAccount() { /* MyBatis 通过无参构造器恢复持久化对象。 */ }

	public UserAccount(UUID id, String email, String displayName, String passwordHash) {
		this.id = id;
		this.email = email;
		this.displayName = displayName;
		this.passwordHash = passwordHash;
	}

	public UUID getId() { return id; }
	public String getEmail() { return email; }
	public String getDisplayName() { return displayName; }
	public String getPasswordHash() { return passwordHash; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
