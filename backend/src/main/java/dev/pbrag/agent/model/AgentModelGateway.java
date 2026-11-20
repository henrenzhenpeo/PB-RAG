package dev.pbrag.agent.model;

import dev.pbrag.tool.AgentTool;
import java.time.Duration;
import java.util.List;

/** 隔离 Spring AI 消息类型，使领域循环可用固定决策进行测试。 */
public interface AgentModelGateway {
	/** 返回启动时加载的 Prompt 正文摘要，写入 Run 冻结配置。 */
	String promptSha256();

	/** 调用模型一次并返回结构化决策；工具执行由 PB-RAG Runtime 负责。 */
	ModelDecision decide(String goal, String workspaceContext, List<ModelExchange> history,
			List<AgentTool> tools, String model, Duration timeout);
}
