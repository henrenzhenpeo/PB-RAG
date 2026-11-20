package dev.pbrag.agent.service.impl;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.domain.AgentRunStatus;
import dev.pbrag.agent.domain.AgentStepStatus;
import dev.pbrag.agent.domain.AgentStepType;
import dev.pbrag.agent.model.AgentModelGateway;
import dev.pbrag.agent.model.ModelDecision;
import dev.pbrag.agent.model.ModelExchange;
import dev.pbrag.agent.service.AgentOrchestrator;
import dev.pbrag.agent.service.AgentRunService;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.tool.AgentTool;
import dev.pbrag.tool.ToolRegistry;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.JacksonException;

/** 单 Agent 结构化 ReAct 循环；数据库条件更新决定真实终态。 */
@Service
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class AgentOrchestratorImpl implements AgentOrchestrator {
	private static final int MAX_ITERATIONS = 4;
	private static final Duration RUN_TIMEOUT = Duration.ofSeconds(60);
	private static final Duration STEP_TIMEOUT = Duration.ofSeconds(20);
	private final AgentRunService runs;
	private final AgentModelGateway model;
	private final ToolRegistry registry;
	private final KnowledgeBaseService knowledgeBases;
	private final ObjectMapper json;
	private final String modelName;
	private final String codeVersion;

	/** 注入持久化边界、模型适配器和显式工具注册表。 */
	public AgentOrchestratorImpl(AgentRunService runs, AgentModelGateway model,
			ToolRegistry registry, KnowledgeBaseService knowledgeBases, ObjectMapper json,
			@Value("${spring.ai.openai.chat.model:qwen3.7-flash}") String modelName,
			@Value("${PBRAG_CODE_VERSION:dev}") String codeVersion) {
		this.runs = runs;
		this.model = model;
		this.registry = registry;
		this.knowledgeBases = knowledgeBases;
		this.json = json;
		this.modelName = modelName;
		this.codeVersion = codeVersion;
	}

	/** 创建冻结配置并立即执行；异常也必须留下明确终态。 */
	@Override
	public UUID start(UUID workspaceId, UUID knowledgeBaseId, String goal) {
		knowledgeBases.require(workspaceId, knowledgeBaseId);
		Map<String, String> allowed = Map.of("calculator", registry.versions().get("calculator"));
		OffsetDateTime deadline = OffsetDateTime.now().plus(RUN_TIMEOUT);
		UUID runId = runs.create(workspaceId, knowledgeBaseId, goal, "knowledge-agent", "0.1.0",
				"agent.knowledge_worker", "0.1.0", "bailian", modelName,
				Map.of("tools", allowed, "maxIterations", MAX_ITERATIONS,
						"runTimeoutSeconds", RUN_TIMEOUT.toSeconds(), "stepTimeoutSeconds", STEP_TIMEOUT.toSeconds(),
						"promptSha256", model.promptSha256(), "codeVersion", codeVersion,
						"modelTemperature", 0.2, "modelMaxTokens", 1024),
				deadline);
		String owner = UUID.randomUUID().toString();
		if (!runs.claim(workspaceId, runId, owner, deadline.plusSeconds(10))) {
			throw new IllegalStateException("New agent run could not be claimed");
		}
		execute(workspaceId, knowledgeBaseId, runId, owner, goal, deadline, allowed);
		return runId;
	}

	/** 每轮只接受原生 Tool Call；完成、循环、取消和超时分别收敛。 */
	private void execute(UUID workspaceId, UUID knowledgeBaseId, UUID runId, String owner,
			String goal, OffsetDateTime deadline, Map<String, String> allowed) {
		List<ModelExchange> history = new ArrayList<>();
		Set<String> seenCalls = new HashSet<>();
		try {
			for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
				checkpoint(workspaceId, knowledgeBaseId, runId, deadline);
				UUID modelStep = runs.startStep(workspaceId, runId, owner, AgentStepType.MODEL_CALL,
						Map.of("iteration", iteration + 1));
				ModelDecision decision;
				try {
					List<AgentTool> tools = allowed.keySet().stream().sorted()
							.map(name -> registry.require(name, allowed)).toList();
					decision = bounded(workspaceId, knowledgeBaseId, runId, deadline,
							() -> model.decide(goal, "workspaceId=" + workspaceId + ", knowledgeBaseId=" + knowledgeBaseId,
									List.copyOf(history), tools, modelName, remaining(deadline)));
					runs.finishStep(workspaceId, modelStep, AgentStepStatus.COMPLETED,
							Map.of("toolCount", decision.toolCalls().size(),
									"toolNames", decision.toolCalls().stream().map(ModelDecision.ToolCall::name).toList(),
									"model", decision.model() == null ? modelName : decision.model()), null);
				}
				catch (RuntimeException failure) {
					runs.finishStep(workspaceId, modelStep, failure instanceof AgentTimeout ? AgentStepStatus.TIMED_OUT :
							AgentStepStatus.FAILED, Map.of(), errorCode(failure));
					throw failure;
				}
				checkpoint(workspaceId, knowledgeBaseId, runId, deadline);
				if (decision.toolCalls().isEmpty()) {
					String answer = decision.answer();
					if (answer == null || answer.isBlank()) throw new AgentFailure("EMPTY_MODEL_ANSWER");
					UUID finalStep = runs.startStep(workspaceId, runId, owner, AgentStepType.FINAL_RESPONSE,
							Map.of("iteration", iteration + 1));
					runs.finishStep(workspaceId, finalStep, AgentStepStatus.COMPLETED,
							Map.of("answerLength", answer.length()), null);
					if (!runs.finish(workspaceId, runId, owner, AgentRunStatus.RUNNING,
							AgentRunStatus.COMPLETED, answer, null, null)) {
						throw new AgentFailure("RUN_STATE_CHANGED");
					}
					return;
				}
				List<ModelExchange.Observation> observations = new ArrayList<>();
				for (ModelDecision.ToolCall call : decision.toolCalls()) {
					checkpoint(workspaceId, knowledgeBaseId, runId, deadline);
					UUID toolStep = runs.startStep(workspaceId, runId, owner, AgentStepType.TOOL_CALL,
							Map.of("tool", call.name() == null ? "" : call.name()));
					UUID execution = null;
					try {
						AgentTool tool = registry.require(call.name(), allowed);
						if (call.id() == null || call.id().isBlank() || call.arguments() == null ||
								call.arguments().length() > 8192) throw new IllegalArgumentException("Invalid tool call envelope");
						JsonNode args = tool.validate(parseArguments(call.arguments()));
						String canonical = canonicalArguments(tool, args, workspaceId, knowledgeBaseId);
						String key = sha256(canonical);
						if (!seenCalls.add(key)) throw new AgentFailure("REPEATED_TOOL_CALL");
						execution = runs.startToolExecution(workspaceId, runId, toolStep, call.id(),
								tool.name(), tool.version(), key, sha256(args.toString()), tool.redactedArguments(args));
						String response = bounded(workspaceId, knowledgeBaseId, runId, deadline,
								() -> tool.execute(workspaceId, knowledgeBaseId, args));
						runs.finishToolExecution(workspaceId, execution, "COMPLETED",
								Map.of("result", response), null);
						runs.finishStep(workspaceId, toolStep, AgentStepStatus.COMPLETED,
								Map.of("tool", tool.name(), "result", response), null);
						observations.add(new ModelExchange.Observation(call.id(), tool.name(), response));
					}
					catch (RuntimeException failure) {
						String code = errorCode(failure);
						if (execution != null) runs.finishToolExecution(workspaceId, execution,
								failure instanceof AgentTimeout ? "TIMED_OUT" : "FAILED", Map.of(), code);
						runs.finishStep(workspaceId, toolStep,
								failure instanceof AgentTimeout ? AgentStepStatus.TIMED_OUT : AgentStepStatus.FAILED,
								Map.of(), code);
						throw failure;
					}
				}
				history.add(new ModelExchange(decision, List.copyOf(observations)));
			}
			throw new AgentFailure("MAX_ITERATIONS");
		}
		catch (RuntimeException failure) {
			AgentRunStatus target = failure instanceof AgentCancelled ? AgentRunStatus.CANCELLED :
					failure instanceof AgentTimeout ? AgentRunStatus.TIMED_OUT : AgentRunStatus.FAILED;
			runs.finish(workspaceId, runId, owner, AgentRunStatus.RUNNING, target,
					null, errorCode(failure), "Agent run stopped: " + errorCode(failure));
		}
	}

	/** 数据库取消意图和总 Deadline 在每个外部操作边界检查。 */
	private void checkpoint(UUID workspaceId, UUID knowledgeBaseId, UUID runId, OffsetDateTime deadline) {
		if (!OffsetDateTime.now().isBefore(deadline)) throw new AgentTimeout();
		AgentRun run = runs.require(workspaceId, knowledgeBaseId, runId);
		if (run.cancelRequestedAt() != null) throw new AgentCancelled();
		if (run.status() != AgentRunStatus.RUNNING) throw new AgentFailure("RUN_STATE_CHANGED");
	}

	/** 在虚拟线程等待外部调用，同时轮询持久化取消意图。 */
	private <T> T bounded(UUID workspaceId, UUID knowledgeBaseId, UUID runId,
			OffsetDateTime deadline, Callable<T> operation) {
		var executor = Executors.newVirtualThreadPerTaskExecutor();
		Future<T> future = executor.submit(operation);
		try {
			OffsetDateTime stepDeadline = OffsetDateTime.now().plus(STEP_TIMEOUT);
			while (true) {
				checkpoint(workspaceId, knowledgeBaseId, runId, deadline);
				if (!OffsetDateTime.now().isBefore(stepDeadline)) throw new AgentTimeout();
				try { return future.get(200, TimeUnit.MILLISECONDS); }
				catch (TimeoutException ignored) { /* 继续检查取消与 Deadline。 */ }
			}
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new AgentCancelled();
		}
		catch (ExecutionException exception) {
			throw exception.getCause() instanceof RuntimeException runtime ? runtime :
					new AgentFailure("EXTERNAL_OPERATION_FAILED");
		}
		finally {
			future.cancel(true);
			executor.shutdownNow();
		}
	}

	private static Duration remaining(OffsetDateTime deadline) {
		Duration remaining = Duration.between(OffsetDateTime.now(), deadline);
		return remaining.compareTo(STEP_TIMEOUT) < 0 ? remaining : STEP_TIMEOUT;
	}

	private static String errorCode(RuntimeException failure) {
		return failure instanceof AgentFailure agentFailure ? agentFailure.getMessage() :
				failure instanceof AgentTimeout ? "DEADLINE_EXCEEDED" :
				failure instanceof AgentCancelled ? "CANCELLED" :
				failure instanceof IllegalArgumentException ? "INVALID_TOOL_ARGUMENTS" : "AGENT_OPERATION_FAILED";
	}

	private JsonNode parseArguments(String arguments) {
		try { return json.readTree(arguments); }
		catch (JacksonException exception) { throw new IllegalArgumentException("Malformed tool arguments", exception); }
	}

	/** 首版仅开放 calculator，以规范化数值构造重复调用和幂等身份。 */
	private static String canonicalArguments(AgentTool tool, JsonNode arguments,
			UUID workspaceId, UUID knowledgeBaseId) {
		BigDecimal left = arguments.get("left").decimalValue().stripTrailingZeros();
		BigDecimal right = arguments.get("right").decimalValue().stripTrailingZeros();
		return tool.name() + ":" + tool.version() + ":" + workspaceId + ":" + knowledgeBaseId + ":" +
				arguments.get("operation").stringValue() + ":" + left.toPlainString() + ":" + right.toPlainString();
	}

	private static String sha256(String value) {
		try {
			byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return java.util.HexFormat.of().formatHex(bytes);
		}
		catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
	}

	private static class AgentFailure extends RuntimeException { AgentFailure(String code) { super(code); } }
	private static class AgentTimeout extends RuntimeException { }
	private static class AgentCancelled extends RuntimeException { }
}
