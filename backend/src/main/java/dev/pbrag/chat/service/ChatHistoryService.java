package dev.pbrag.chat.service;

import dev.pbrag.chat.domain.ChatSessionView;
import dev.pbrag.chat.domain.RagCitation;
import java.util.List;
import java.util.UUID;

/** 管理用户问答会话、消息终态和版本级引用快照。 */
public interface ChatHistoryService {

	/** 创建或续接会话，并原子写入用户问题与流式助手占位消息。 */
	StartedTurn startTurn(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID sessionId, String question);

	/** 保存实际进入模型上下文的引用及原文摘录。 */
	void saveCitations(StartedTurn turn, List<RagCitation> citations);

	/** 将助手消息标记为正常完成并保存完整正文。 */
	void complete(StartedTurn turn, String answer);

	/** 将助手消息标记为中断，保留已生成正文和稳定错误码。 */
	void interrupt(StartedTurn turn, String partialAnswer, String errorCode);

	/** 加载当前用户在指定知识库中的会话快照。 */
	ChatSessionView require(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId, UUID sessionId);

	/** 一轮持久化问答的稳定标识。 */
	record StartedTurn(UUID workspaceId, UUID sessionId, UUID turnId, UUID assistantMessageId) {
	}
}
