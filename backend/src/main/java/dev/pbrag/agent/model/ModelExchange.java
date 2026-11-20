package dev.pbrag.agent.model;

import java.util.List;

/** 已完成的一轮决策及其工具观察结果，供下一轮模型上下文重建。 */
public record ModelExchange(ModelDecision decision, List<Observation> observations) {
	/** 对应原生 Tool Call ID 的受限工具响应。 */
	public record Observation(String callId, String toolName, String response) { }
}
