package dev.pbrag.agent.domain;

/** Agent 运行生命周期；终态一旦写入便不可再次推进。 */
public enum AgentRunStatus {
	PENDING,
	RUNNING,
	WAITING_APPROVAL,
	COMPLETED,
	FAILED,
	CANCELLED,
	TIMED_OUT;

	/** 返回当前状态是否为不可逆终态。 */
	public boolean terminal() {
		return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMED_OUT;
	}
}
