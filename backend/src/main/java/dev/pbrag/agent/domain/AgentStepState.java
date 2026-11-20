package dev.pbrag.agent.domain;

/** Agent Step 状态机；每条记录代表一次执行尝试。 */
public record AgentStepState(AgentStepStatus status) {

	public AgentStepState {
		if (status == null) throw new IllegalArgumentException("Step status is required");
	}

	/** 创建尚未执行的 Step 状态。 */
	public static AgentStepState pending() {
		return new AgentStepState(AgentStepStatus.PENDING);
	}

	/** 应用 Step 动作并拒绝终态覆盖。 */
	public AgentStepState apply(AgentStepAction action) {
		if (action == null) throw new IllegalArgumentException("Step action is required");
		return switch (action) {
			case START -> require(status == AgentStepStatus.PENDING, AgentStepStatus.RUNNING, action);
			case COMPLETE -> require(status == AgentStepStatus.RUNNING, AgentStepStatus.COMPLETED, action);
			case FAIL -> require(active(), AgentStepStatus.FAILED, action);
			case CANCEL -> require(active(), AgentStepStatus.CANCELLED, action);
			case TIME_OUT -> require(active(), AgentStepStatus.TIMED_OUT, action);
		};
	}

	private boolean active() {
		return status == AgentStepStatus.PENDING || status == AgentStepStatus.RUNNING;
	}

	private AgentStepState require(boolean allowed, AgentStepStatus next, AgentStepAction action) {
		if (!allowed) {
			throw new IllegalStateException("Action %s is not allowed from %s".formatted(action, status));
		}
		return new AgentStepState(next);
	}
}
