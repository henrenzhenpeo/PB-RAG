package dev.pbrag.agent.api;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.service.AgentOrchestrator;
import dev.pbrag.agent.service.AgentRunService;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 首版同步 Agent 运行入口及受 Workspace 约束的查询与取消。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/agent-runs")
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai")
public class AgentRunController {
	private final AgentOrchestrator orchestrator;
	private final AgentRunService runs;

	/** 注入领域编排与运行事实服务。 */
	public AgentRunController(AgentOrchestrator orchestrator, AgentRunService runs) {
		this.orchestrator = orchestrator;
		this.runs = runs;
	}

	/** 创建并同步执行最小 ReAct 运行，返回终态与运行 ID。 */
	@PostMapping
	public ResponseEntity<ApiResponse<RunView>> start(@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId, @Valid @RequestBody StartRequest request) {
		UUID id = orchestrator.start(workspaceId, knowledgeBaseId, request.goal());
		return ApiResponses.ok(RunView.from(runs.require(workspaceId, knowledgeBaseId, id)));
	}

	/** 获取 Run 状态及用户可见最终结果。 */
	@GetMapping("/{runId}")
	public ResponseEntity<ApiResponse<RunView>> get(@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId, @PathVariable UUID runId) {
		return ApiResponses.ok(RunView.from(runs.require(workspaceId, knowledgeBaseId, runId)));
	}

	/** 先校验父资源，再持久化协作式取消请求。 */
	@PostMapping("/{runId}/cancel")
	public ResponseEntity<ApiResponse<RunView>> cancel(@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId, @PathVariable UUID runId) {
		runs.require(workspaceId, knowledgeBaseId, runId);
		runs.requestCancellation(workspaceId, runId);
		return ApiResponses.ok(RunView.from(runs.require(workspaceId, knowledgeBaseId, runId)));
	}

	/** 限制用户目标长度，防止无界 Prompt 和运行记录。 */
	public record StartRequest(@NotBlank @Size(max = 2000) String goal) { }

	/** 对外只提供必要状态，不暴露冻结配置中的内部内容。 */
	public record RunView(UUID id, String status, String answer, String errorCode,
			java.time.OffsetDateTime createdAt, java.time.OffsetDateTime completedAt) {
		/** 从作用域内的运行事实构建响应。 */
		static RunView from(AgentRun run) {
			return new RunView(run.id(), run.status().name(), run.finalAnswer(), run.errorCode(),
					run.createdAt(), run.completedAt());
		}
	}
}
