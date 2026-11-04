package dev.pbrag.identity.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Workspace 是知识资源的逻辑分组根，不代表租户或授权边界。
 * 入库、检索、引用和 Trace 仍需携带其 ID，以保持资源归属一致。
 */
@TableName("workspaces")
public class Workspace {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private String name;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	protected Workspace() {
		// MyBatis 通过无参构造器恢复持久化对象。
	}

	public Workspace(UUID id, String name) {
		this.id = id;
		this.name = name;
	}

	public UUID id() {
		return id;
	}

	public String name() {
		return name;
	}

	public OffsetDateTime createdAt() {
		return createdAt;
	}

	public OffsetDateTime updatedAt() {
		return updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
