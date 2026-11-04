package dev.pbrag.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Workspace 内的知识容器。workspaceId 是资源归属与持久化查询不可省略的条件。
 */
@TableName("knowledge_bases")
public class KnowledgeBase {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private String name;
	private String description;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	protected KnowledgeBase() {
		// MyBatis 通过无参构造器恢复持久化对象。
	}

	public KnowledgeBase(UUID id, UUID workspaceId, String name, String description) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.name = name;
		this.description = description;
	}

	public UUID id() {
		return id;
	}

	public UUID workspaceId() {
		return workspaceId;
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
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

	public UUID getWorkspaceId() {
		return workspaceId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
