package dev.pbrag.agent.service.impl;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.domain.AgentRunAction;
import dev.pbrag.agent.domain.AgentRunState;
import dev.pbrag.agent.domain.AgentRunStatus;
import dev.pbrag.agent.domain.AgentStepStatus;
import dev.pbrag.agent.domain.AgentStepType;
import dev.pbrag.agent.mapper.AgentRunMapper;
import dev.pbrag.agent.service.AgentRunService;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 通过原子条件更新持久化 Agent 事实，阻止取消、租约与完成之间的状态覆盖。 */
@Service
public class AgentRunServiceImpl implements AgentRunService {
	private final AgentRunMapper mapper;
	private final ObjectMapper objectMapper;

	/** 注入 Mapper 和受控 JSON 序列化器。 */
	public AgentRunServiceImpl(AgentRunMapper mapper, ObjectMapper objectMapper) {
		this.mapper = mapper;
		this.objectMapper = objectMapper;
	}

	/** 创建一个尚未领取的运行，配置在此后不可修改。 */
	@Override
	@Transactional
	public UUID create(UUID workspaceId, UUID knowledgeBaseId, String goal, String agentKey,
			String agentVersion, String promptId, String promptVersion, String modelProvider,
			String modelName, Map<String, Object> configuration, OffsetDateTime deadlineAt) {
		if (deadlineAt == null || !deadlineAt.isAfter(OffsetDateTime.now())) {
			throw new IllegalArgumentException("Agent deadline must be in the future");
		}
		UUID id = UUID.randomUUID();
		if (mapper.insertRun(id, workspaceId, knowledgeBaseId, goal, agentKey, agentVersion,
				promptId, promptVersion, modelProvider, modelName, json(configuration), deadlineAt) != 1) {
			throw new IllegalStateException("Failed to create agent run");
		}
		return id;
	}

	/** 仅返回指定知识库内的运行，避免跨 Workspace 错误关联。 */
	@Override
	@Transactional(readOnly = true)
	public AgentRun require(UUID workspaceId, UUID knowledgeBaseId, UUID runId) {
		AgentRun run = mapper.selectRun(workspaceId, knowledgeBaseId, runId);
		if (run == null) throw new ResourceNotFoundException("Agent run not found");
		return run;
	}

	/** 领取仍有效的运行租约。 */
	@Override
	@Transactional
	public boolean claim(UUID workspaceId, UUID runId, String owner, OffsetDateTime leaseExpiresAt) {
		if (owner == null || owner.isBlank() || leaseExpiresAt == null ||
				!leaseExpiresAt.isAfter(OffsetDateTime.now())) {
			throw new IllegalArgumentException("Valid lease owner and expiry are required");
		}
		return mapper.claim(workspaceId, runId, owner, leaseExpiresAt) == 1;
	}

	/** 只允许持有者续租。 */
	@Override
	@Transactional
	public boolean renew(UUID workspaceId, UUID runId, String owner, OffsetDateTime leaseExpiresAt) {
		return mapper.renew(workspaceId, runId, owner, leaseExpiresAt) == 1;
	}

	/** 先保存取消请求，再由 Worker 收敛终态。 */
	@Override
	@Transactional
	public boolean requestCancellation(UUID workspaceId, UUID runId) {
		return mapper.requestCancellation(workspaceId, runId) == 1;
	}

	/** 验证领域迁移并条件写入；竞争失败由调用者重新读取实际状态。 */
	@Override
	@Transactional
	public boolean finish(UUID workspaceId, UUID runId, String owner, AgentRunStatus expected,
			AgentRunStatus next, String answer, String errorCode, String errorMessage) {
		if (!next.terminal()) throw new IllegalArgumentException("Only a terminal status can finish a run");
		AgentRunAction action = switch (next) {
			case COMPLETED -> AgentRunAction.COMPLETE;
			case FAILED -> AgentRunAction.FAIL;
			case CANCELLED -> AgentRunAction.CANCEL;
			case TIMED_OUT -> AgentRunAction.TIME_OUT;
			default -> throw new IllegalArgumentException("Unsupported terminal status");
		};
		new AgentRunState(expected).apply(action);
		if (next == AgentRunStatus.COMPLETED && (answer == null || answer.isBlank())) {
			throw new IllegalArgumentException("Completed run requires an answer");
		}
		return mapper.finish(workspaceId, runId, owner, expected, next, answer, errorCode,
				limit(errorMessage)) == 1;
	}

	/** 序号分配与 Step 创建位于同一事务，失败时序号回滚。 */
	@Override
	@Transactional
	public UUID startStep(UUID workspaceId, UUID runId, String owner,
			AgentStepType type, Map<String, Object> inputSummary) {
		Integer sequence = mapper.nextStepSequence(workspaceId, runId, owner);
		if (sequence == null) throw new IllegalStateException("Agent run lease is not active");
		UUID id = UUID.randomUUID();
		if (mapper.insertStep(id, workspaceId, runId, sequence, type, json(inputSummary)) != 1) {
			throw new IllegalStateException("Failed to start agent step");
		}
		return id;
	}

	/** Step 终态只可写入一次。 */
	@Override
	@Transactional
	public void finishStep(UUID workspaceId, UUID stepId, AgentStepStatus status,
			Map<String, Object> outputSummary, String errorCode) {
		if (!status.terminal()) throw new IllegalArgumentException("Step status must be terminal");
		if (mapper.finishStep(workspaceId, stepId, status, json(outputSummary), errorCode) != 1) {
			throw new IllegalStateException("Agent step is no longer running");
		}
	}

	/** 插入唯一调用身份，数据库约束拒绝重复执行。 */
	@Override
	@Transactional
	public UUID startToolExecution(UUID workspaceId, UUID runId, UUID stepId, String toolCallId,
			String toolName, String toolVersion, String idempotencyKey,
			String argumentsHash, Map<String, Object> redactedArguments) {
		UUID id = UUID.randomUUID();
		if (mapper.insertToolExecution(id, workspaceId, runId, stepId, toolCallId,
				toolName, toolVersion, idempotencyKey, argumentsHash, json(redactedArguments)) != 1) {
			throw new IllegalStateException("Failed to create tool execution");
		}
		return id;
	}

	/** 记录受限 Observation；完整工具正文不进入普通轨迹表。 */
	@Override
	@Transactional
	public void finishToolExecution(UUID workspaceId, UUID executionId, String status,
			Map<String, Object> observation, String errorCode) {
		if (mapper.finishToolExecution(workspaceId, executionId, status, json(observation), errorCode) != 1) {
			throw new IllegalStateException("Tool execution is no longer running");
		}
	}

	private String json(Object value) {
		try { return objectMapper.writeValueAsString(value); }
		catch (JacksonException exception) { throw new IllegalStateException("Cannot serialize agent metadata", exception); }
	}

	private static String limit(String value) {
		return value == null || value.length() <= 500 ? value : value.substring(0, 500);
	}
}
