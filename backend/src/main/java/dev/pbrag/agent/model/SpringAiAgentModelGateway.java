package dev.pbrag.agent.model;

import dev.pbrag.tool.AgentTool;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/** Spring AI 模型协议适配；只声明工具 Schema，不让模型客户端执行工具。 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class SpringAiAgentModelGateway implements AgentModelGateway {
	private final ChatModel model;
	private final String systemPrompt;
	private final String promptSha256;

	/** 载入仓库版本化 Prompt 的正文。 */
	public SpringAiAgentModelGateway(ChatModel model,
			@Value("classpath:prompts/agent/knowledge_agent.md") Resource resource) throws IOException {
		this.model = model;
		String content = resource.getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n");
		int separator = content.indexOf("\n---\n");
		if (separator < 0) throw new IllegalStateException("Agent prompt metadata separator is missing");
		this.systemPrompt = content.substring(separator + 5).strip();
		try {
			this.promptSha256 = java.util.HexFormat.of().formatHex(
					MessageDigest.getInstance("SHA-256").digest(systemPrompt.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
	}

	/** Prompt 正文 SHA-256，用于定位实际运行版本。 */
	@Override public String promptSha256() { return promptSha256; }

	/** 每次调用都显式提供本 Run 白名单，模型输出交回领域层执行。 */
	@Override
	public ModelDecision decide(String goal, String workspaceContext, List<ModelExchange> history,
			List<AgentTool> tools, String modelName, Duration timeout) {
		List<Message> messages = new ArrayList<>();
		messages.add(new SystemMessage(systemPrompt.replace("{{workspace_context}}", workspaceContext)
				.replace("{{available_tools}}", tools.stream().map(AgentTool::name).toList().toString())
				.replace("{{conversation_context}}", "")
				.replace("{{user_goal}}", goal)));
		messages.add(new UserMessage(goal));
		for (ModelExchange exchange : history) {
			List<AssistantMessage.ToolCall> calls = exchange.decision().toolCalls().stream()
					.map(call -> new AssistantMessage.ToolCall(call.id(), "function", call.name(), call.arguments()))
					.toList();
			messages.add(AssistantMessage.builder().content(
					exchange.decision().answer() == null ? "" : exchange.decision().answer()).toolCalls(calls).build());
			messages.add(ToolResponseMessage.builder().responses(exchange.observations().stream()
					.map(observation -> new ToolResponseMessage.ToolResponse(
							observation.callId(), observation.toolName(), observation.response()))
					.toList()).build());
		}
		OpenAiChatOptions options = OpenAiChatOptions.builder().model(modelName).timeout(timeout)
				.temperature(0.2).maxTokens(1024)
				.parallelToolCalls(false).toolCallbacks(tools.stream()
						.map(tool -> (ToolCallback) new SchemaOnlyCallback(tool)).toList()).build();
		ChatResponse response = model.call(new Prompt(messages, options));
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
			throw new IllegalStateException("Model returned no decision");
		}
		AssistantMessage output = response.getResult().getOutput();
		var usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
		return new ModelDecision(output.getText(), output.getToolCalls().stream()
				.map(call -> new ModelDecision.ToolCall(call.id(), call.name(), call.arguments())).toList(),
				response.getMetadata() == null ? modelName : response.getMetadata().getModel(),
				usage == null ? null : usage.getPromptTokens(),
				usage == null ? null : usage.getCompletionTokens());
	}

	/** 如果 Spring AI 尝试自动执行工具，立即失败，防止绕过 PB-RAG 的持久化与策略。 */
	private record SchemaOnlyCallback(AgentTool tool) implements ToolCallback {
		@Override public ToolDefinition getToolDefinition() {
			return ToolDefinition.builder().name(tool.name()).description(tool.description())
					.inputSchema(tool.inputSchema()).build();
		}
		@Override public String call(String arguments) {
			throw new IllegalStateException("Tool execution must pass through PB-RAG Runtime");
		}
	}
}
