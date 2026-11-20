package dev.pbrag.agent.domain;

/** Agent Step 允许的状态迁移动作。 */
public enum AgentStepAction {
	START,
	COMPLETE,
	FAIL,
	CANCEL,
	TIME_OUT
}
