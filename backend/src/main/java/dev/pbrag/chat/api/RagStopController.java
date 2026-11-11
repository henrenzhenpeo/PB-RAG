package dev.pbrag.chat.api;

import dev.pbrag.chat.service.RagChatService;
import dev.pbrag.chat.service.ChatHistoryService.StopResult;
import dev.pbrag.identity.api.AuthController;
import dev.pbrag.identity.service.UserAccountService.User;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 为正在运行的 RAG 回答提供带用户和资源作用域校验的显式停止入口。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/chat-sessions")
@ConditionalOnProperty(
		name = { "spring.ai.model.chat", "spring.ai.model.embedding" },
		havingValue = "openai")
public class RagStopController {

	private final RagChatService service;

	/** 注入同时管理持久化终态和运行时取消信号的 RAG 服务。 */
	public RagStopController(RagChatService service) {
		this.service = service;
	}

	/** 幂等停止一条正在生成的助手消息，并保证只能操作当前用户自己的会话。 */
	@PostMapping("/{sessionId}/messages/{messageId}/stop")
	public ResponseEntity<ApiResponse<StopResponse>> stop(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId,
			@PathVariable UUID sessionId,
			@PathVariable UUID messageId,
			HttpServletRequest request) {
		User user = AuthController.requiredUser(request);
		StopResult result = service.stop(user.id(), workspaceId, knowledgeBaseId, sessionId, messageId);
		return ApiResponses.ok(new StopResponse(
				sessionId, messageId, result.status(), result.errorCode()));
	}

	/** 停止成功后的稳定终态，供前端立即更新当前轮次。 */
	public record StopResponse(UUID sessionId, UUID messageId, String status, String errorCode) {
	}
}
