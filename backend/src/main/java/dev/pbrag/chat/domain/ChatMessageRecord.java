package dev.pbrag.chat.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/** 一条持久化问答消息，用 turnId 将用户问题与助手回答配对。 */
public record ChatMessageRecord(
		UUID id,
		UUID turnId,
		String role,
		String status,
		String content,
		String errorCode,
		OffsetDateTime createdAt) {
}
