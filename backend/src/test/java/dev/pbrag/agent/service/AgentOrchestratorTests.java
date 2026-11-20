package dev.pbrag.agent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.agent.domain.AgentRun;
import dev.pbrag.agent.domain.AgentRunStatus;
import dev.pbrag.agent.model.AgentModelGateway;
import dev.pbrag.agent.model.ModelDecision;
import dev.pbrag.agent.service.impl.AgentOrchestratorImpl;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
import dev.pbrag.tool.CalculatorTool;
import dev.pbrag.tool.ToolRegistry;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

/** 固定模型决策验证 calculator → Observation → 最终回答的领域循环。 */
class AgentOrchestratorTests {
	@Test
	void calculatorObservationFeedsSecondModelDecision() {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID runId = UUID.randomUUID();
		AgentRunService runs = mock(AgentRunService.class);
		AgentModelGateway model = mock(AgentModelGateway.class);
		KnowledgeBaseService knowledgeBases = mock(KnowledgeBaseService.class);
		AgentRun run = mock(AgentRun.class);
		when(run.status()).thenReturn(AgentRunStatus.RUNNING);
		when(runs.require(workspaceId, knowledgeBaseId, runId)).thenReturn(run);
		when(runs.create(eq(workspaceId), eq(knowledgeBaseId), any(), any(), any(), any(),
				any(), any(), any(), any(), any())).thenReturn(runId);
		when(runs.claim(eq(workspaceId), eq(runId), any(), any())).thenReturn(true);
		when(runs.startStep(eq(workspaceId), eq(runId), any(), any(), any())).thenAnswer(invocation -> UUID.randomUUID());
		when(runs.startToolExecution(eq(workspaceId), eq(runId), any(), any(), any(), any(), any(),
				any(), any())).thenReturn(UUID.randomUUID());
		when(runs.finish(eq(workspaceId), eq(runId), any(), eq(AgentRunStatus.RUNNING),
				eq(AgentRunStatus.COMPLETED), eq("结果是 4"), isNull(), isNull())).thenReturn(true);
		when(model.promptSha256()).thenReturn("digest");
		when(model.decide(any(), any(), any(), any(), any(), any()))
				.thenReturn(new ModelDecision("", List.of(new ModelDecision.ToolCall(
						"call-1", "calculator", "{\"operation\":\"divide\",\"left\":12,\"right\":3}")),
						"qwen3.7-flash", 10, 4))
				.thenReturn(new ModelDecision("结果是 4", List.of(), "qwen3.7-flash", 20, 5));

		AgentOrchestratorImpl orchestrator = new AgentOrchestratorImpl(runs, model,
				new ToolRegistry(List.of(new CalculatorTool())), knowledgeBases,
				new ObjectMapper(), "qwen3.7-flash", "test");
		assertEquals(runId, orchestrator.start(workspaceId, knowledgeBaseId, "计算 12 除以 3"));
		ArgumentCaptor<List> history = ArgumentCaptor.forClass(List.class);
		verify(model, org.mockito.Mockito.times(2)).decide(any(), any(), history.capture(), any(), any(), any());
		assertEquals(0, history.getAllValues().get(0).size());
		assertEquals(1, history.getAllValues().get(1).size());
	}
}
