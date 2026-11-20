package dev.pbrag.agent.domain;

/** 单次不可变执行尝试的状态。重试必须创建新的 attempt，不能覆盖失败事实。 */
public enum AgentStepStatus {
	PENDING,
	RUNNING,
	COMPLETED,
	FAILED,
	CANCELLED,
	TIMED_OUT;

	/** 返回 Step 是否已经进入终态。 */
	public boolean terminal() {
		return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
	}
}
