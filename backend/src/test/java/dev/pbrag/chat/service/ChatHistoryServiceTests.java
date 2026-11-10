package dev.pbrag.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.chat.mapper.ChatHistoryMapper;
import dev.pbrag.chat.service.ChatHistoryService.StartedTurn;
import dev.pbrag.chat.service.impl.ChatHistoryServiceImpl;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 会话创建和消息终态持久化的事务编排回归测试。 */
class ChatHistoryServiceTests {

	@Test
	void createsSessionUserMessageAndStreamingAssistantAtomically() {
		UUID userId = UUID.randomUUID();
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
		ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
		when(mapper.insertSession(any(), eq(workspaceId), eq(knowledgeBaseId), eq(userId), eq("question")))
				.thenReturn(1);
		when(mapper.insertMessage(any(), eq(workspaceId), any(), any(), eq("USER"), eq("COMPLETED"), eq("question")))
				.thenReturn(1);
		when(mapper.insertMessage(any(), eq(workspaceId), any(), any(), eq("ASSISTANT"), eq("STREAMING"), eq("")))
				.thenReturn(1);
		ChatHistoryService service = new ChatHistoryServiceImpl(knowledgeBaseService, mapper);

		StartedTurn turn = service.startTurn(userId, workspaceId, knowledgeBaseId, null, " question ");

		assertThat(turn.workspaceId()).isEqualTo(workspaceId);
		assertThat(turn.sessionId()).isNotNull();
		assertThat(turn.turnId()).isNotNull();
		assertThat(turn.assistantMessageId()).isNotNull();
		verify(knowledgeBaseService).require(workspaceId, knowledgeBaseId);
		verify(mapper).touchSession(workspaceId, turn.sessionId());
	}
}
