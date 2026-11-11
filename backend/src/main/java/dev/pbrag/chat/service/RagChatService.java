package dev.pbrag.chat.service;

import dev.pbrag.chat.domain.RagStreamEvent;
import dev.pbrag.chat.service.ChatHistoryService.StopResult;
import java.util.UUID;
import reactor.core.publisher.Flux;

/** 编排检索、上下文构造、流式模型回答和引用输出。 */
public interface RagChatService {

	/** 在指定知识库内回答问题，并按结构化事件持续输出结果。 */
	Flux<RagStreamEvent> answer(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID sessionId, String question);

	/** 持久化用户停止状态，并向当前进程中的对应模型流发送取消信号。 */
	StopResult stop(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID sessionId, UUID assistantMessageId);
}
