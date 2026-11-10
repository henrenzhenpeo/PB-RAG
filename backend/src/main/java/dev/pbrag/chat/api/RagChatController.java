package dev.pbrag.chat.api;

import dev.pbrag.chat.domain.RagStreamEvent;
import dev.pbrag.chat.service.RagChatService;
import dev.pbrag.identity.api.AuthController;
import dev.pbrag.identity.service.UserAccountService.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/** 在 Workspace 和知识库作用域内提供带引用的流式 RAG 问答。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/rag")
@ConditionalOnProperty(
		name = { "spring.ai.model.chat", "spring.ai.model.embedding" },
		havingValue = "openai")
public class RagChatController {

	private final RagChatService service;

	/** 注入 RAG 问答编排服务。 */
	public RagChatController(RagChatService service) {
		this.service = service;
	}

	/** 接收用户问题，并以具名 SSE 事件返回引用、文本增量和完成状态。 */
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<ServerSentEvent<RagStreamEvent>> answer(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId,
			@Valid @RequestBody RagQuestion input,
			HttpServletRequest request) {
		User user = AuthController.requiredUser(request);
		return service.answer(user.id(), workspaceId, knowledgeBaseId, input.sessionId(), input.question())
				.map(event -> ServerSentEvent.<RagStreamEvent>builder(event)
						.event(event.type())
						.build());
	}

	/** RAG 问题请求，长度限制与服务层防御性校验保持一致。 */
	public record RagQuestion(
			UUID sessionId,
			@NotBlank(message = "问题不能为空")
			@Size(max = 2000, message = "问题不能超过 2000 个字符")
			String question) {
	}
}
