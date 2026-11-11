package dev.pbrag.chat.service.impl;

import dev.pbrag.chat.domain.RagCitation;
import dev.pbrag.chat.domain.RagStreamEvent;
import dev.pbrag.chat.service.ChatHistoryService;
import dev.pbrag.chat.service.ChatHistoryService.StartedTurn;
import dev.pbrag.chat.service.ChatHistoryService.StopResult;
import dev.pbrag.chat.service.RagCancellationRegistry;
import dev.pbrag.chat.service.RagCancellationRegistry.CancellationHandle;
import dev.pbrag.chat.service.RagChatService;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.service.RetrievalService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** PB-RAG 最小问答闭环：自研检索和引用编排，模型只负责基于证据生成回答。 */
@Service
@ConditionalOnProperty(
		name = { "spring.ai.model.chat", "spring.ai.model.embedding" },
		havingValue = "openai")
public class RagChatServiceImpl implements RagChatService {

	private static final Logger log = LoggerFactory.getLogger(RagChatServiceImpl.class);
	private static final String PROMPT_BODY_MARKER = "\n---\n";
	private static final char UTF_8_BOM = '\uFEFF';
	private final RetrievalService retrievalService;
	private final ChatHistoryService historyService;
	private final RagCancellationRegistry cancellationRegistry;
	private final StreamingChatModel chatModel;
	private final String systemPrompt;

	/** 注入检索和流式模型，并在启动时加载版本化运行时 Prompt。 */
	public RagChatServiceImpl(
			RetrievalService retrievalService,
			ChatHistoryService historyService,
			RagCancellationRegistry cancellationRegistry,
			StreamingChatModel chatModel,
			@Value("classpath:prompts/rag/answer_with_citations.md") Resource promptResource)
			throws IOException {
		this.retrievalService = retrievalService;
		this.historyService = historyService;
		this.cancellationRegistry = cancellationRegistry;
		this.chatModel = chatModel;
		this.systemPrompt = promptBody(promptResource.getContentAsString(StandardCharsets.UTF_8));
	}

	/**
	 * 检索发生在模型调用之前；没有合格证据时由确定性代码直接拒答，避免模型在空上下文中猜测。
	 * 发送给前端的引用只包含实际进入 Prompt 的证据。
	 */
	@Override
	public Flux<RagStreamEvent> answer(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID sessionId, String question) {
		return Flux.defer(() -> {
			StartedTurn turn = historyService.startTurn(
					userId, workspaceId, knowledgeBaseId, sessionId, question);
			StringBuffer answer = new StringBuffer();
			CancellationHandle cancellation = cancellationRegistry.register(
					turn.assistantMessageId(), answer::toString);
			AtomicBoolean finalized = new AtomicBoolean();
			Flux<RagStreamEvent> body = Flux.defer(() -> answerAfterRetrieval(
					workspaceId, knowledgeBaseId, question, turn, answer, finalized))
					.onErrorResume(exception -> retrievalFailure(
							workspaceId, knowledgeBaseId, turn, answer, finalized, exception))
					.takeUntilOther(cancellation.cancellation());
			return Flux.concat(
					Flux.just(
							RagStreamEvent.started(turn.sessionId(), turn.assistantMessageId()),
							RagStreamEvent.status("正在检索知识库…")),
					body)
					.doFinally(signal -> {
						cancellationRegistry.release(cancellation);
						if (signal == reactor.core.publisher.SignalType.CANCEL
								&& finalized.compareAndSet(false, true)) {
							Schedulers.boundedElastic().schedule(() ->
									historyService.interrupt(
											turn, answer.toString(), "CLIENT_CANCELLED"));
						}
					});
		}).subscribeOn(Schedulers.boundedElastic())
				.onErrorResume(exception -> {
					log.warn("rag_session_start_failed workspaceId={} knowledgeBaseId={} errorType={}",
							workspaceId, knowledgeBaseId, exception.getClass().getSimpleName());
					return Flux.just(RagStreamEvent.error(
							"RAG_SESSION_FAILED", "无法创建或恢复问答会话，请刷新后重试"));
				});
	}

	/** 先持久化确定性的停止终态，再通知本实例中的模型流释放资源。 */
	@Override
	public StopResult stop(
			UUID userId, UUID workspaceId, UUID knowledgeBaseId,
			UUID sessionId, UUID assistantMessageId) {
		String partialAnswer = cancellationRegistry.partialAnswer(assistantMessageId);
		StopResult result = historyService.stop(
				userId, workspaceId, knowledgeBaseId, sessionId, assistantMessageId, partialAnswer);
		cancellationRegistry.cancel(assistantMessageId);
		log.info("rag_answer_stop_requested workspaceId={} knowledgeBaseId={} sessionId={} messageId={} status={}",
				workspaceId, knowledgeBaseId, sessionId, assistantMessageId, result.status());
		return result;
	}

	/** 在订阅 SSE 后执行检索，使检索或映射异常也能转换为结构化流错误。 */
	private Flux<RagStreamEvent> answerAfterRetrieval(
			UUID workspaceId, UUID knowledgeBaseId, String question,
			StartedTurn turn, StringBuffer answer, AtomicBoolean finalized) {
		List<RetrievalHit> evidence = retrievalService.search(workspaceId, knowledgeBaseId, question);
		List<RagCitation> citations = citations(evidence);
		historyService.saveCitations(turn, citations);
		if (evidence.isEmpty()) {
			String refusal = "当前知识库中没有找到足够相关的证据，无法可靠回答这个问题。";
			answer.append(refusal);
			historyService.complete(turn, refusal);
			finalized.set(true);
			return Flux.just(
					RagStreamEvent.citations(citations),
					RagStreamEvent.delta(refusal),
					RagStreamEvent.completed());
		}

		Prompt prompt = new Prompt(
				new SystemMessage(systemPrompt),
				new UserMessage(userMessage(question, evidence)));
		Flux<RagStreamEvent> deltas = chatModel.stream(prompt)
				.handle((response, sink) -> {
					String text = responseText(response);
					// 部分模型会在流末尾发送只有用量或完成原因的空文本帧；它不是失败事件。
					if (text != null && !text.isEmpty()) {
						answer.append(text);
						sink.next(RagStreamEvent.delta(text));
					}
				});

		log.info("rag_answer_started workspaceId={} knowledgeBaseId={} evidenceCount={}",
				workspaceId, knowledgeBaseId, evidence.size());
		Flux<RagStreamEvent> completion = Mono.fromRunnable(() -> {
			if (finalized.compareAndSet(false, true)) {
				try {
					historyService.complete(turn, answer.toString());
				}
				catch (RuntimeException exception) {
					finalized.set(false);
					throw exception;
				}
			}
		}).subscribeOn(Schedulers.boundedElastic()).thenReturn(RagStreamEvent.completed()).flux();
		Flux<RagStreamEvent> generation = Flux.concat(deltas, completion)
				.onErrorResume(exception -> {
					log.warn("rag_generation_interrupted workspaceId={} knowledgeBaseId={} errorType={}",
							workspaceId, knowledgeBaseId, exception.getClass().getSimpleName());
					return persistInterruption(
							turn, answer, finalized, "RAG_GENERATION_INTERRUPTED",
							"回答生成中断，可重试本次问题");
				});
		return Flux.concat(
				Flux.just(
						RagStreamEvent.citations(citations),
						RagStreamEvent.status("已找到 " + citations.size() + " 条相关证据，正在生成回答…")),
				generation);
	}

	/** 将检索异常持久化为本轮中断，并返回稳定的 SSE 错误。 */
	private Flux<RagStreamEvent> retrievalFailure(
			UUID workspaceId, UUID knowledgeBaseId, StartedTurn turn,
			StringBuffer answer, AtomicBoolean finalized, Throwable exception) {
		log.warn("rag_retrieval_failed workspaceId={} knowledgeBaseId={} errorType={}",
				workspaceId, knowledgeBaseId, exception.getClass().getSimpleName());
		return persistInterruption(
				turn, answer, finalized, "RAG_RETRIEVAL_FAILED", "知识检索暂时失败，请稍后重试");
	}

	/** 在弹性线程执行中断落库，完成后发送结构化错误事件。 */
	private Flux<RagStreamEvent> persistInterruption(
			StartedTurn turn, StringBuffer answer, AtomicBoolean finalized,
			String errorCode, String message) {
		return Mono.fromRunnable(() -> {
			if (finalized.compareAndSet(false, true)) {
				try {
					historyService.interrupt(turn, answer.toString(), errorCode);
				}
				catch (RuntimeException exception) {
					finalized.set(false);
					throw exception;
				}
			}
		}).subscribeOn(Schedulers.boundedElastic())
				.thenReturn(RagStreamEvent.error(errorCode, message))
				.flux();
	}

	/** 将证据转换为包含受限原文摘录的稳定引用元数据。 */
	private static List<RagCitation> citations(List<RetrievalHit> hits) {
		List<RagCitation> citations = new ArrayList<>(hits.size());
		for (int index = 0; index < hits.size(); index++) {
			RetrievalHit hit = hits.get(index);
			citations.add(new RagCitation(
					index + 1, hit.chunkId(), hit.documentId(), hit.documentVersionId(),
					hit.documentName(), hit.sequenceNumber(), hit.sourcePosition(),
					excerpt(hit.content()), hit.similarity()));
		}
		return List.copyOf(citations);
	}

	/** 生成最多 500 字符的引用原文，避免 SSE 和历史表被超长 Chunk 放大。 */
	private static String excerpt(String content) {
		String normalized = content == null ? "" : content.strip();
		return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "…";
	}

	/** 使用编号和明确数据边界组装不可信知识库内容。 */
	private static String userMessage(String question, List<RetrievalHit> evidence) {
		StringBuilder context = new StringBuilder();
		for (int index = 0; index < evidence.size(); index++) {
			RetrievalHit hit = evidence.get(index);
			context.append("\n[证据 ").append(index + 1).append("] 文档=")
					.append(hit.documentName()).append("，版本=").append(hit.documentVersionId())
					.append("\n<document-content>\n").append(hit.content())
					.append("\n</document-content>\n");
		}
		return "<retrieved-evidence>\n" + context + "</retrieved-evidence>\n\n<user-question>\n"
				+ question + "\n</user-question>";
	}

	/** 从 Spring AI 的流式响应中安全提取本次文本增量。 */
	private static String responseText(ChatResponse response) {
		if (response == null || response.getResult() == null || response.getResult().getOutput() == null) return null;
		return response.getResult().getOutput().getText();
	}

	/**
	 * 去除 Prompt 文件的元数据头，只把分隔线之后的运行时规则发送给模型。
	 * 先统一换行并移除可选 BOM，避免 Windows 检出为 CRLF 后启动失败。
	 */
	private static String promptBody(String promptFile) {
		String normalized = promptFile.replace("\r\n", "\n").replace('\r', '\n');
		if (!normalized.isEmpty() && normalized.charAt(0) == UTF_8_BOM) {
			normalized = normalized.substring(1);
		}
		int marker = normalized.indexOf(PROMPT_BODY_MARKER);
		if (marker < 0) throw new IllegalStateException("RAG prompt metadata separator is missing");
		String body = normalized.substring(marker + PROMPT_BODY_MARKER.length()).strip();
		if (body.isEmpty()) throw new IllegalStateException("RAG prompt body is empty");
		return body;
	}
}
