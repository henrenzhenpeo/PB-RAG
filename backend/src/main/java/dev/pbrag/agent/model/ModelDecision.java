package dev.pbrag.agent.model;

import java.util.List;

/** 一轮模型输出的可见答案或结构化工具调用，不包含隐藏思维。 */
public record ModelDecision(String answer, List<ToolCall> toolCalls, String model,
		Integer promptTokens, Integer completionTokens) {
	/** 模型原生工具调用身份及 JSON 参数。 */
	public record ToolCall(String id, String name, String arguments) { }
}
