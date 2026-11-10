package dev.pbrag.chat.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 持久化会话的只读投影，用于校验用户与知识库作用域。 */
public record ChatSessionRecord(
		UUID id,
		UUID workspaceId,
		UUID knowledgeBaseId,
		UUID userId,
		String title,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
}
