package dev.pbrag.agent.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Agent Run 持久化视图；配置 JSON 是创建运行时冻结的可复现快照。 */
public class AgentRun {
	private UUID id;
	private UUID workspaceId;
	private UUID knowledgeBaseId;
	private String goal;
	private AgentRunStatus status;
	private String agentKey;
	private String agentVersion;
	private String promptId;
	private String promptVersion;
	private String modelProvider;
	private String modelName;
	private String configuration;
	private Integer nextStepSequence;
	private OffsetDateTime deadlineAt;
	private OffsetDateTime cancelRequestedAt;
	private String leaseOwner;
	private OffsetDateTime leaseExpiresAt;
	private String errorCode;
	private String errorMessage;
	private String finalAnswer;
	private OffsetDateTime createdAt;
	private OffsetDateTime startedAt;
	private OffsetDateTime completedAt;
	private OffsetDateTime updatedAt;

	/** 供 MyBatis 反射恢复数据库记录。 */
	protected AgentRun() {
	}

	public UUID id() { return id; }
	public UUID workspaceId() { return workspaceId; }
	public UUID knowledgeBaseId() { return knowledgeBaseId; }
	public String goal() { return goal; }
	public AgentRunStatus status() { return status; }
	public String agentKey() { return agentKey; }
	public String agentVersion() { return agentVersion; }
	public String promptId() { return promptId; }
	public String promptVersion() { return promptVersion; }
	public String modelProvider() { return modelProvider; }
	public String modelName() { return modelName; }
	public String configuration() { return configuration; }
	public Integer nextStepSequence() { return nextStepSequence; }
	public OffsetDateTime deadlineAt() { return deadlineAt; }
	public OffsetDateTime cancelRequestedAt() { return cancelRequestedAt; }
	public String leaseOwner() { return leaseOwner; }
	public OffsetDateTime leaseExpiresAt() { return leaseExpiresAt; }
	public String errorCode() { return errorCode; }
	public String errorMessage() { return errorMessage; }
	public String finalAnswer() { return finalAnswer; }
	public OffsetDateTime createdAt() { return createdAt; }
	public OffsetDateTime startedAt() { return startedAt; }
	public OffsetDateTime completedAt() { return completedAt; }
	public OffsetDateTime updatedAt() { return updatedAt; }
}
