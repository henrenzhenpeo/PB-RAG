package dev.pbrag.knowledge.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

@TableName("document_versions")
public class DocumentVersion {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private UUID documentId;
	private int versionNumber;
	private String contentSha256;
	private String storageKey;
	private String parserVersion;
	private OffsetDateTime createdAt;

	protected DocumentVersion() {
	}

	public DocumentVersion(
			UUID id, UUID workspaceId, UUID documentId, int versionNumber,
			String contentSha256, String storageKey) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.documentId = documentId;
		this.versionNumber = versionNumber;
		this.contentSha256 = contentSha256;
		this.storageKey = storageKey;
	}

	public UUID id() { return id; }
	public UUID workspaceId() { return workspaceId; }
	public UUID documentId() { return documentId; }
	public int versionNumber() { return versionNumber; }
	public String contentSha256() { return contentSha256; }
	public String storageKey() { return storageKey; }
	public String parserVersion() { return parserVersion; }
	public OffsetDateTime createdAt() { return createdAt; }
	public UUID getId() { return id; }
	public UUID getWorkspaceId() { return workspaceId; }
	public UUID getDocumentId() { return documentId; }
	public int getVersionNumber() { return versionNumber; }
	public String getContentSha256() { return contentSha256; }
	public String getStorageKey() { return storageKey; }
	public String getParserVersion() { return parserVersion; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
}
