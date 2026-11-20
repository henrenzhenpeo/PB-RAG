package dev.pbrag.tool;

import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/** PB-RAG 内部工具契约；模型协议与工具执行策略由 Runtime 分开控制。 */
public interface AgentTool {
	/** 稳定工具名称。 */
	String name();
	/** 影响回放的工具版本。 */
	String version();
	/** 展示给模型的工具用途。 */
	String description();
	/** 模型参数的 JSON Schema。 */
	String inputSchema();
	/** 校验并规范化参数；非法参数必须抛出异常。 */
	JsonNode validate(JsonNode arguments);
	/** 在代码确定的 Workspace 和知识库范围内执行。 */
	String execute(UUID workspaceId, UUID knowledgeBaseId, JsonNode arguments);
	/** 返回不含原始敏感参数的审计摘要。 */
	default Map<String, Object> redactedArguments(JsonNode arguments) {
		return Map.of("validated", true);
	}
}
