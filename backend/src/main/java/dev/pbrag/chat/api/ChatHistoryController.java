package dev.pbrag.chat.api;

import dev.pbrag.chat.domain.ChatSessionView;
import dev.pbrag.chat.service.ChatHistoryService;
import dev.pbrag.identity.api.AuthController;
import dev.pbrag.identity.service.UserAccountService.User;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 读取当前登录用户的持久化知识问答会话。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/chat-sessions")
public class ChatHistoryController {

	private final ChatHistoryService service;

	/** 注入会话历史服务。 */
	public ChatHistoryController(ChatHistoryService service) {
		this.service = service;
	}

	/** 按用户和资源作用域恢复一段会话的消息与引用。 */
	@GetMapping("/{sessionId}")
	public ResponseEntity<ApiResponse<ChatSessionView>> get(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId,
			@PathVariable UUID sessionId,
			HttpServletRequest request) {
		User user = AuthController.requiredUser(request);
		return ApiResponses.ok(service.require(
				user.id(), workspaceId, knowledgeBaseId, sessionId));
	}
}
