package dev.pbrag.agent.domain;

/** 可回放 Step 类型；Observation 归属于 TOOL_CALL 的结构化输出。 */
public enum AgentStepType {
	MODEL_CALL,
	TOOL_CALL,
	FINAL_RESPONSE
}
