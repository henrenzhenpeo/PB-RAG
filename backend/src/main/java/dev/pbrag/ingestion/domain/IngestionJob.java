package dev.pbrag.ingestion.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.OffsetDateTime;
import java.util.UUID;

/** 异步入库任务持久化对象，包含状态、阶段、重试次数、租约和结构化错误。 */
@TableName("ingestion_jobs")
public class IngestionJob {

	@TableId(type = IdType.INPUT)
	private UUID id;
	private UUID workspaceId;
	private UUID documentVersionId;
	private IngestionJobStatus status;
	private IngestionStage stage;
	private int attemptCount;
	private int maxAttempts;
	private OffsetDateTime availableAt;
	private String leaseOwner;
	private OffsetDateTime leaseExpiresAt;
	private String errorCode;
	private String errorMessage;
	private OffsetDateTime createdAt;
	private OffsetDateTime updatedAt;

	protected IngestionJob() {
	}

	public IngestionJob(UUID id, UUID workspaceId, UUID documentVersionId) {
		this.id = id;
		this.workspaceId = workspaceId;
		this.documentVersionId = documentVersionId;
		this.status = IngestionJobStatus.PENDING;
		this.stage = IngestionStage.PENDING;
		this.attemptCount = 0;
		this.maxAttempts = 3;
		this.availableAt = OffsetDateTime.now();
	}

	public UUID id() { return id; }
	public UUID workspaceId() { return workspaceId; }
	public UUID documentVersionId() { return documentVersionId; }
	public IngestionJobStatus status() { return status; }
	public IngestionStage stage() { return stage; }
	public int attemptCount() { return attemptCount; }
	public int maxAttempts() { return maxAttempts; }
	public OffsetDateTime availableAt() { return availableAt; }
	public String errorCode() { return errorCode; }
	public String errorMessage() { return errorMessage; }
	public OffsetDateTime createdAt() { return createdAt; }
	public OffsetDateTime updatedAt() { return updatedAt; }
	public UUID getId() { return id; }
	public UUID getWorkspaceId() { return workspaceId; }
	public UUID getDocumentVersionId() { return documentVersionId; }
	public IngestionJobStatus getStatus() { return status; }
	public IngestionStage getStage() { return stage; }
	public int getAttemptCount() { return attemptCount; }
	public int getMaxAttempts() { return maxAttempts; }
	public OffsetDateTime getAvailableAt() { return availableAt; }
	public String getLeaseOwner() { return leaseOwner; }
	public OffsetDateTime getLeaseExpiresAt() { return leaseExpiresAt; }
	public String getErrorCode() { return errorCode; }
	public String getErrorMessage() { return errorMessage; }
	public OffsetDateTime getCreatedAt() { return createdAt; }
	public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
