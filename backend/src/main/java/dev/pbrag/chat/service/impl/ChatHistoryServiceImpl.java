package dev.pbrag.chat.service.impl;

import dev.pbrag.chat.domain.ChatMessageRecord;
import dev.pbrag.chat.domain.ChatSessionRecord;
import dev.pbrag.chat.domain.ChatSessionView;
import dev.pbrag.chat.domain.RagCitation;
import dev.pbrag.chat.mapper.ChatHistoryMapper;
import dev.pbrag.chat.service.ChatHistoryService;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.platform.api.InvalidRequestException;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 会话历史的事务实现，确保每轮问题、助手占位和引用具有一致归属。 */
@Service
public class ChatHistoryServiceImpl implements ChatHistoryService {

	private final KnowledgeBaseService knowledgeBaseService;
	private final ChatHistoryMapper mapper;

	/** 注入父资源校验服务和问答历史 Mapper。 */
	public ChatHistoryServiceImpl(KnowledgeBaseService knowledgeBaseService, ChatHistoryMapper mapper) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.mapper = mapper;
	}

	/** 创建会话时生成标题；续接时严格验证用户、Workspace 与知识库归属。 */
	@Override
	@Transactional
	public StartedTurn startTurn(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID requestedSessionId, String question) {
		String normalizedQuestion = normalizeQuestion(question);
		knowledgeBaseService.require(workspaceId, knowledgeBaseId);
		UUID sessionId = requestedSessionId;
		if (sessionId == null) {
			sessionId = UUID.randomUUID();
			if (mapper.insertSession(
					sessionId, workspaceId, knowledgeBaseId, userId, title(normalizedQuestion)) != 1) {
				throw new IllegalStateException("Failed to create chat session");
			}
		}
		else {
			requireSession(userId, workspaceId, knowledgeBaseId, sessionId);
		}

		UUID turnId = UUID.randomUUID();
		UUID userMessageId = UUID.randomUUID();
		UUID assistantMessageId = UUID.randomUUID();
		if (mapper.insertMessage(
				userMessageId, workspaceId, sessionId, turnId,
				"USER", "COMPLETED", normalizedQuestion) != 1
				|| mapper.insertMessage(
				assistantMessageId, workspaceId, sessionId, turnId,
				"ASSISTANT", "STREAMING", "") != 1) {
			throw new IllegalStateException("Failed to create chat turn");
		}
		mapper.touchSession(workspaceId, sessionId);
		return new StartedTurn(workspaceId, sessionId, turnId, assistantMessageId);
	}

	/** 引用批次与助手消息在同一事务内写入，任一失败时整批回滚。 */
	@Override
	@Transactional
	public void saveCitations(StartedTurn turn, List<RagCitation> citations) {
		for (RagCitation citation : citations) {
			if (mapper.insertCitation(
					UUID.randomUUID(), turn.workspaceId(), turn.assistantMessageId(), citation) != 1) {
				throw new IllegalStateException("Failed to persist citation " + citation.index());
			}
		}
	}

	/** 正常完成只允许从 STREAMING 转换一次。 */
	@Override
	@Transactional
	public void complete(StartedTurn turn, String answer) {
		finish(turn, "COMPLETED", answer, null);
	}

	/** 中断保留部分正文，避免断流后丢失用户已看到的内容。 */
	@Override
	@Transactional
	public void interrupt(StartedTurn turn, String partialAnswer, String errorCode) {
		finish(turn, "INTERRUPTED", partialAnswer, errorCode);
	}

	/** 加载前先收敛重启遗留状态，再按 turnId 合并用户消息、助手消息和引用。 */
	@Override
	@Transactional
	public ChatSessionView require(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId, UUID sessionId) {
		ChatSessionRecord session = requireSession(userId, workspaceId, knowledgeBaseId, sessionId);
		mapper.interruptStaleStreaming(workspaceId, sessionId);
		Map<UUID, TurnAccumulator> turns = new LinkedHashMap<>();
		for (ChatMessageRecord message : mapper.selectMessages(workspaceId, sessionId)) {
			TurnAccumulator turn = turns.computeIfAbsent(
					message.turnId(), ignored -> new TurnAccumulator(message.turnId(), message.createdAt()));
			if ("USER".equals(message.role())) turn.user = message;
			if ("ASSISTANT".equals(message.role())) {
				turn.assistant = message;
				turn.citations = mapper.selectCitations(workspaceId, message.id());
			}
		}
		List<ChatSessionView.Turn> snapshots = turns.values().stream()
				.filter(turn -> turn.user != null && turn.assistant != null)
				.map(TurnAccumulator::snapshot)
				.toList();
		return new ChatSessionView(
				session.id(), session.workspaceId(), session.knowledgeBaseId(), session.title(),
				snapshots, session.createdAt(), session.updatedAt());
	}

	/** 执行消息终态更新并刷新会话活动时间。 */
	private void finish(StartedTurn turn, String status, String content, String errorCode) {
		UUID workspaceId = turn.workspaceId();
		if (mapper.finishAssistant(
				workspaceId, turn.assistantMessageId(), status,
				content == null ? "" : content, errorCode) != 1) {
			throw new IllegalStateException("Assistant message is no longer streaming");
		}
		mapper.touchSession(workspaceId, turn.sessionId());
	}

	/** 查询并验证会话完整资源归属，避免通过会话 ID 跨用户读取。 */
	private ChatSessionRecord requireSession(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId, UUID sessionId) {
		ChatSessionRecord session = mapper.selectSession(
				sessionId, workspaceId, knowledgeBaseId, userId);
		if (session == null) throw new ResourceNotFoundException("Chat session not found");
		return session;
	}

	/** 校验问题并统一首尾空白。 */
	private static String normalizeQuestion(String question) {
		if (question == null || question.isBlank()) throw new InvalidRequestException("问题不能为空");
		String value = question.strip();
		if (value.length() > 2000) throw new InvalidRequestException("问题不能超过 2000 个字符");
		return value;
	}

	/** 从首个问题生成稳定、受限长度的会话标题。 */
	private static String title(String question) {
		return question.length() <= 160 ? question : question.substring(0, 160);
	}

	/** 聚合同一 turnId 下的两条消息。 */
	private static final class TurnAccumulator {
		private final UUID turnId;
		private final OffsetDateTime createdAt;
		private ChatMessageRecord user;
		private ChatMessageRecord assistant;
		private List<RagCitation> citations = List.of();

		/** 创建尚未聚合完成的一轮会话。 */
		private TurnAccumulator(UUID turnId, OffsetDateTime createdAt) {
			this.turnId = turnId;
			this.createdAt = createdAt;
		}

		/** 生成只读页面快照。 */
		private ChatSessionView.Turn snapshot() {
			return new ChatSessionView.Turn(
					turnId, user.id(), assistant.id(), user.content(), assistant.content(),
					assistant.status(), assistant.errorCode(), List.copyOf(citations), createdAt);
		}
	}
}
