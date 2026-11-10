package dev.pbrag.chat.domain;

import java.util.List;
import java.util.UUID;

/** RAG 流式协议事件，明确区分阶段状态、引用、文本增量、完成和结构化错误。 */
public record RagStreamEvent(
		String type,
		String text,
		List<RagCitation> citations,
		String errorCode,
		UUID sessionId,
		UUID messageId) {

	/** 创建持久化会话和助手消息已经建立的事件。 */
	public static RagStreamEvent started(UUID sessionId, UUID messageId) {
		return new RagStreamEvent("started", null, null, null, sessionId, messageId);
	}

	/** 创建可直接展示的处理阶段事件，让检索期间也有即时反馈。 */
	public static RagStreamEvent status(String message) {
		return new RagStreamEvent("status", message, null, null, null, null);
	}

	/** 创建检索完成后的引用快照事件。 */
	public static RagStreamEvent citations(List<RagCitation> citations) {
		return new RagStreamEvent("citations", null, List.copyOf(citations), null, null, null);
	}

	/** 创建一段模型回答增量。 */
	public static RagStreamEvent delta(String text) {
		return new RagStreamEvent("delta", text, null, null, null, null);
	}

	/** 创建流正常完成事件。 */
	public static RagStreamEvent completed() {
		return new RagStreamEvent("completed", null, null, null, null, null);
	}

	/** 创建不暴露第三方异常细节的流式错误事件。 */
	public static RagStreamEvent error(String code, String message) {
		return new RagStreamEvent("error", message, null, code, null, null);
	}
}
