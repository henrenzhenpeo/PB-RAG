package dev.pbrag.chat.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import dev.pbrag.chat.domain.RagStreamEvent;
import dev.pbrag.chat.service.ChatHistoryService.StartedTurn;
import dev.pbrag.chat.service.impl.RagChatServiceImpl;
import dev.pbrag.retrieval.service.RetrievalService;
import dev.pbrag.retrieval.domain.RetrievalHit;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ByteArrayResource;
import reactor.core.publisher.Flux;

/** RAG 编排的确定性拒答和流式协议回归测试。 */
class RagChatServiceTests {

	@Test
	void refusesWithoutCallingTheModelWhenRetrievalHasNoEvidence() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		RetrievalService retrievalService = mock(RetrievalService.class);
		ChatHistoryService historyService = mock(ChatHistoryService.class);
		StreamingChatModel chatModel = mock(StreamingChatModel.class);
		StartedTurn turn = new StartedTurn(workspaceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(historyService.startTurn(userId, workspaceId, knowledgeBaseId, null, "unknown")).thenReturn(turn);
		when(retrievalService.search(workspaceId, knowledgeBaseId, "unknown"))
				.thenReturn(List.of());
		ByteArrayResource prompt = new ByteArrayResource(
				"---\nid: test\n---\nOnly use evidence."
						.getBytes(StandardCharsets.UTF_8));
		RagChatService service = new RagChatServiceImpl(
				retrievalService, historyService, chatModel, prompt, 12000);

		List<RagStreamEvent> events = service.answer(userId, workspaceId, knowledgeBaseId, null, "unknown")
				.collectList().block();

		assertThat(events).extracting(RagStreamEvent::type)
				.containsExactly("started", "status", "citations", "delta", "completed");
		assertThat(events.get(1).text()).contains("正在检索");
		assertThat(events.get(3).text()).contains("没有找到足够相关的证据");
		verifyNoInteractions(chatModel);
	}

	@Test
	void returnsStructuredStreamErrorWhenRetrievalFails() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		RetrievalService retrievalService = mock(RetrievalService.class);
		ChatHistoryService historyService = mock(ChatHistoryService.class);
		StreamingChatModel chatModel = mock(StreamingChatModel.class);
		StartedTurn turn = new StartedTurn(workspaceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(historyService.startTurn(userId, workspaceId, knowledgeBaseId, null, "question")).thenReturn(turn);
		when(retrievalService.search(workspaceId, knowledgeBaseId, "question"))
				.thenThrow(new IllegalStateException("mapping failed"));
		ByteArrayResource prompt = new ByteArrayResource(
				"---\nid: test\n---\nOnly use evidence."
						.getBytes(StandardCharsets.UTF_8));
		RagChatService service = new RagChatServiceImpl(
				retrievalService, historyService, chatModel, prompt, 12000);

		List<RagStreamEvent> events = service.answer(userId, workspaceId, knowledgeBaseId, null, "question")
				.collectList().block();

		assertThat(events).extracting(RagStreamEvent::type).containsExactly("started", "status", "error");
		assertThat(events.get(2)).satisfies(event -> {
			assertThat(event.type()).isEqualTo("error");
			assertThat(event.errorCode()).isEqualTo("RAG_RETRIEVAL_FAILED");
			assertThat(event.text()).contains("知识检索暂时失败");
		});
		verifyNoInteractions(chatModel);
	}

	@Test
	void distinguishesGenerationInterruptionFromRetrievalFailure() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		RetrievalService retrievalService = mock(RetrievalService.class);
		ChatHistoryService historyService = mock(ChatHistoryService.class);
		StreamingChatModel chatModel = mock(StreamingChatModel.class);
		StartedTurn turn = new StartedTurn(workspaceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(historyService.startTurn(userId, workspaceId, knowledgeBaseId, null, "question")).thenReturn(turn);
		when(retrievalService.search(workspaceId, knowledgeBaseId, "question"))
				.thenReturn(List.of(new RetrievalHit(
						UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
						"guide.md", 0, "evidence", "{}", 0.9)));
		when(chatModel.stream(any(Prompt.class)))
				.thenReturn(Flux.error(new IllegalStateException("upstream disconnected")));
		ByteArrayResource prompt = new ByteArrayResource(
				"---\nid: test\n---\nOnly use evidence.".getBytes(StandardCharsets.UTF_8));
		RagChatService service = new RagChatServiceImpl(retrievalService, historyService, chatModel, prompt, 12000);

		List<RagStreamEvent> events = service.answer(userId, workspaceId, knowledgeBaseId, null, "question")
				.collectList().block();

		assertThat(events).extracting(RagStreamEvent::type)
				.containsExactly("started", "status", "citations", "status", "error");
		assertThat(events.get(4).errorCode()).isEqualTo("RAG_GENERATION_INTERRUPTED");
	}

	@Test
	void ignoresMetadataOnlyFramesAtTheEndOfTheModelStream() throws Exception {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		RetrievalService retrievalService = mock(RetrievalService.class);
		ChatHistoryService historyService = mock(ChatHistoryService.class);
		StreamingChatModel chatModel = mock(StreamingChatModel.class);
		StartedTurn turn = new StartedTurn(workspaceId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		when(historyService.startTurn(userId, workspaceId, knowledgeBaseId, null, "question")).thenReturn(turn);
		when(retrievalService.search(workspaceId, knowledgeBaseId, "question"))
				.thenReturn(List.of(new RetrievalHit(
						UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
						"guide.md", 0, "evidence", "{}", 0.9)));
		when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
				new ChatResponse(List.of(new Generation(new AssistantMessage("answer")))),
				new ChatResponse(List.of())));
		ByteArrayResource prompt = new ByteArrayResource(
				"---\nid: test\n---\nOnly use evidence.".getBytes(StandardCharsets.UTF_8));
		RagChatService service = new RagChatServiceImpl(retrievalService, historyService, chatModel, prompt, 12000);

		List<RagStreamEvent> events = service.answer(userId, workspaceId, knowledgeBaseId, null, "question")
				.collectList().block();

		assertThat(events).extracting(RagStreamEvent::type)
				.containsExactly("started", "status", "citations", "status", "delta", "completed");
		assertThat(events.get(3).text()).contains("1 条相关证据");
		assertThat(events.get(4).text()).isEqualTo("answer");
	}
}
