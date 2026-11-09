package dev.pbrag.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("documents")
public class Document {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private UUID knowledgeBaseId;
	private String displayName;
	private String mediaType;
	private UUID currentVersionId;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	protected Document() {
	}

	public Document(UUID id, UUID workspaceId, UUID knowledgeBaseId, String displayName, String mediaType) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.knowledgeBaseId = knowledgeBaseId;
		this.displayName = displayName;
		this.mediaType = mediaType;
	}

	public UUID id() { return id; }
	public UUID workspaceId() { return workspaceId; }
	public UUID knowledgeBaseId() { return knowledgeBaseId; }
	public String displayName() { return displayName; }
	public String mediaType() { return mediaType; }
	public UUID currentVersionId() { return currentVersionId; }
	public OffsetDateTime createdAt() { return createdAt; }
	public OffsetDateTime updatedAt() { return updatedAt; }
	public UUID getId() { return id; }
	public UUID getWorkspaceId() { return workspaceId; }
	public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
	public String getDisplayName() { return displayName; }
	public String getMediaType() { return mediaType; }
	public UUID getCurrentVersionId() { return currentVersionId; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
