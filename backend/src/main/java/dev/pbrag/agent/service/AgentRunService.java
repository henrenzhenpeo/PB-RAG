package dev.pbrag.agent.service;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.domain.AgentRunStatus;
import dev.pbrag.agent.domain.AgentStepStatus;
import dev.pbrag.agent.domain.AgentStepType;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** Agent Run、Step 与 Tool Execution 的事务事实边界。 */
public interface AgentRunService {
	/** 创建运行并冻结模型、Prompt、工具和限制配置。 */
	UUID create(UUID workspaceId, UUID knowledgeBaseId, String goal, String agentKey,
			String agentVersion, String promptId, String promptVersion, String modelProvider,
			String modelName, Map<String, Object> configuration, OffsetDateTime deadlineAt);

	/** 按 Workspace 和知识库范围查询运行。 */
	AgentRun require(UUID workspaceId, UUID knowledgeBaseId, UUID runId);

	/** 领取运行；失败表示其他 Worker 持有租约或状态已改变。 */
	boolean claim(UUID workspaceId, UUID runId, String owner, OffsetDateTime leaseExpiresAt);

	/** 续租运行。 */
	boolean renew(UUID workspaceId, UUID runId, String owner, OffsetDateTime leaseExpiresAt);

	/** 保存取消意图。 */
	boolean requestCancellation(UUID workspaceId, UUID runId);

	/** 条件收敛终态。 */
	boolean finish(UUID workspaceId, UUID runId, String owner, AgentRunStatus expected,
			AgentRunStatus next, String answer, String errorCode, String errorMessage);

	/** 分配序号并创建一次执行尝试。 */
	UUID startStep(UUID workspaceId, UUID runId, String owner, AgentStepType type, Map<String, Object> inputSummary);

	/** 保存 Step 的最终摘要。 */
	void finishStep(UUID workspaceId, UUID stepId, AgentStepStatus status,
			Map<String, Object> outputSummary, String errorCode);

	/** 按调用身份创建工具执行事实；唯一键禁止重复副作用。 */
	UUID startToolExecution(UUID workspaceId, UUID runId, UUID stepId, String toolCallId,
			String toolName, String toolVersion, String idempotencyKey,
			String argumentsHash, Map<String, Object> redactedArguments);

	/** 保存工具 Observation 的受限摘要。 */
	void finishToolExecution(UUID workspaceId, UUID executionId, String status,
			Map<String, Object> observation, String errorCode);
}
