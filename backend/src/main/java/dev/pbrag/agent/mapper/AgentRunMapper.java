package dev.pbrag.agent.mapper;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.domain.AgentRunStatus;
import dev.pbrag.agent.domain.AgentStepStatus;
import dev.pbrag.agent.domain.AgentStepType;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Agent 运行事实的数据库访问；所有写入均带 Workspace 和期望状态。 */
@Mapper
public interface AgentRunMapper {

	/** 创建冻结配置的待运行任务。 */
	int insertRun(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId, @Param("goal") String goal,
			@Param("agentKey") String agentKey, @Param("agentVersion") String agentVersion,
			@Param("promptId") String promptId, @Param("promptVersion") String promptVersion,
			@Param("modelProvider") String modelProvider, @Param("modelName") String modelName,
			@Param("configuration") String configuration, @Param("deadlineAt") OffsetDateTime deadlineAt);

	/** 在 Workspace 与知识库范围内读取运行记录。 */
	AgentRun selectRun(@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId, @Param("runId") UUID runId);

	/** 条件领取任务；租约是跨实例互斥与崩溃恢复的边界。 */
	int claim(@Param("workspaceId") UUID workspaceId, @Param("runId") UUID runId,
			@Param("owner") String owner, @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);

	/** 仅当前租约持有者能延长运行权。 */
	int renew(@Param("workspaceId") UUID workspaceId, @Param("runId") UUID runId,
			@Param("owner") String owner, @Param("leaseExpiresAt") OffsetDateTime leaseExpiresAt);

	/** 先持久化取消意图，使运行中的外部调用能协作式停止。 */
	int requestCancellation(@Param("workspaceId") UUID workspaceId, @Param("runId") UUID runId);

	/** 在期望状态及租约下收敛终态；完成不能越过取消请求。 */
	int finish(@Param("workspaceId") UUID workspaceId, @Param("runId") UUID runId,
			@Param("owner") String owner, @Param("expected") AgentRunStatus expected,
			@Param("next") AgentRunStatus next, @Param("answer") String answer,
			@Param("errorCode") String errorCode, @Param("errorMessage") String errorMessage);

	/** 原子分配 Step 序号，避免并发恢复生成相同序号。 */
	Integer nextStepSequence(@Param("workspaceId") UUID workspaceId,
			@Param("runId") UUID runId, @Param("owner") String owner);

	/** 创建一次执行尝试，不存储原始敏感参数。 */
	int insertStep(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
			@Param("runId") UUID runId, @Param("sequence") int sequence,
			@Param("type") AgentStepType type, @Param("summary") String summary);

	/** 只允许未完成的 Step 写入最终结果。 */
	int finishStep(@Param("workspaceId") UUID workspaceId, @Param("stepId") UUID stepId,
			@Param("status") AgentStepStatus status, @Param("summary") String summary,
			@Param("errorCode") String errorCode);

	/** 记录一个结构化工具调用的唯一执行身份。 */
	int insertToolExecution(@Param("id") UUID id, @Param("workspaceId") UUID workspaceId,
			@Param("runId") UUID runId, @Param("stepId") UUID stepId,
			@Param("toolCallId") String toolCallId, @Param("toolName") String toolName,
			@Param("toolVersion") String toolVersion, @Param("idempotencyKey") String idempotencyKey,
			@Param("argumentsHash") String argumentsHash, @Param("argumentsRedacted") String argumentsRedacted);

	/** 记录工具观察结果或结构化错误。 */
	int finishToolExecution(@Param("workspaceId") UUID workspaceId, @Param("executionId") UUID executionId,
			@Param("status") String status, @Param("observation") String observation,
			@Param("errorCode") String errorCode);
}
