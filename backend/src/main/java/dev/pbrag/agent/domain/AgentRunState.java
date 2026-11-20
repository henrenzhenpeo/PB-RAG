package dev.pbrag.agent.domain;

import java.util.EnumSet;

/** Agent Run 领域状态机，集中约束暂停、恢复和终态迁移。 */
public record AgentRunState(AgentRunStatus status) {

	public AgentRunState {
		if (status == null) throw new IllegalArgumentException("Run status is required");
	}

	/** 创建尚未被 Worker 领取的初始状态。 */
	public static AgentRunState pending() {
		return new AgentRunState(AgentRunStatus.PENDING);
	}

	/** 应用领域动作；非法迁移立即失败，避免服务层绕过状态机。 */
	public AgentRunState apply(AgentRunAction action) {
		if (action == null) throw new IllegalArgumentException("Run action is required");
		return switch (action) {
			case START -> require(status == AgentRunStatus.PENDING, AgentRunStatus.RUNNING, action);
			case WAIT_FOR_APPROVAL -> require(
					status == AgentRunStatus.RUNNING, AgentRunStatus.WAITING_APPROVAL, action);
			case RESUME -> require(
					status == AgentRunStatus.WAITING_APPROVAL, AgentRunStatus.RUNNING, action);
			case COMPLETE -> require(status == AgentRunStatus.RUNNING, AgentRunStatus.COMPLETED, action);
			case FAIL -> require(active(), AgentRunStatus.FAILED, action);
			case CANCEL -> require(active(), AgentRunStatus.CANCELLED, action);
			case TIME_OUT -> require(active(), AgentRunStatus.TIMED_OUT, action);
		};
	}

	private boolean active() {
		return EnumSet.of(
				AgentRunStatus.PENDING,
				AgentRunStatus.RUNNING,
				AgentRunStatus.WAITING_APPROVAL).contains(status);
	}

	private AgentRunState require(boolean allowed, AgentRunStatus next, AgentRunAction action) {
		if (!allowed) {
			throw new IllegalStateException("Action %s is not allowed from %s".formatted(action, status));
		}
		return new AgentRunState(next);
	}
}
