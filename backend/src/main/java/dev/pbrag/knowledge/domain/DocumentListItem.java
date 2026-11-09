package dev.pbrag.knowledge.domain;

import dev.pbrag.ingestion.domain.IngestionJobStatus;
import dev.pbrag.ingestion.domain.IngestionStage;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 知识库文档列表的只读投影，包含当前版本及其入库任务。 */
public class DocumentListItem {

	private UUID documentId;
	private UUID workspaceId;
	private UUID knowledgeBaseId;
	private String displayName;
	private String mediaType;
	private OffsetDateTime documentCreatedAt;
	private OffsetDateTime documentUpdatedAt;
	private UUID versionId;
	private int versionNumber;
	private String contentSha256;
	private OffsetDateTime versionCreatedAt;
	private UUID jobId;
	private IngestionJobStatus jobStatus;
	private IngestionStage jobStage;
	private int attemptCount;
	private int maxAttempts;
	private String errorCode;
	private String errorMessage;
	private OffsetDateTime availableAt;
	private OffsetDateTime jobCreatedAt;
	private OffsetDateTime jobUpdatedAt;

	public UUID getDocumentId() { return documentId; }
	public UUID getWorkspaceId() { return workspaceId; }
	public UUID getKnowledgeBaseId() { return knowledgeBaseId; }
	public String getDisplayName() { return displayName; }
	public String getMediaType() { return mediaType; }
	public OffsetDateTime getDocumentCreatedAt() { return documentCreatedAt; }
	public OffsetDateTime getDocumentUpdatedAt() { return documentUpdatedAt; }
	public UUID getVersionId() { return versionId; }
	public int getVersionNumber() { return versionNumber; }
	public String getContentSha256() { return contentSha256; }
	public OffsetDateTime getVersionCreatedAt() { return versionCreatedAt; }
	public UUID getJobId() { return jobId; }
	public IngestionJobStatus getJobStatus() { return jobStatus; }
	public IngestionStage getJobStage() { return jobStage; }
	public int getAttemptCount() { return attemptCount; }
	public int getMaxAttempts() { return maxAttempts; }
	public String getErrorCode() { return errorCode; }
	public String getErrorMessage() { return errorMessage; }
	public OffsetDateTime getAvailableAt() { return availableAt; }
	public OffsetDateTime getJobCreatedAt() { return jobCreatedAt; }
	public OffsetDateTime getJobUpdatedAt() { return jobUpdatedAt; }
}
