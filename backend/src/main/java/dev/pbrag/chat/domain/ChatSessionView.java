package dev.pbrag.chat.domain;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** 可恢复到问答页面的会话快照。 */
public record ChatSessionView(
		UUID id,
		UUID workspaceId,
		UUID knowledgeBaseId,
		String title,
		List<Turn> turns,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/** 一轮已持久化问答及其引用。 */
	public record Turn(
			UUID turnId,
			UUID userMessageId,
			UUID assistantMessageId,
			String question,
			String answer,
			String status,
			String errorCode,
			List<RagCitation> citations,
			OffsetDateTime createdAt) {
	}
}
