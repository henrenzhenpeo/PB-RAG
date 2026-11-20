package dev.pbrag.agent.domain;

/** Agent Run 允许的领域动作。取消请求单独持久化，确认停止后才执行 CANCEL。 */
public enum AgentRunAction {
	START,
	WAIT_FOR_APPROVAL,
	RESUME,
	COMPLETE,
	FAIL,
	CANCEL,
	TIME_OUT
}
