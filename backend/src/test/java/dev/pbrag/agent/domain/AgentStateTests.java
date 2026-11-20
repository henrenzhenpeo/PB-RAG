package dev.pbrag.agent.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** 终态不可覆盖及审批暂停迁移的领域回归。 */
class AgentStateTests {
	@Test
	void runMustReachExplicitTerminalState() {
		AgentRunState running = AgentRunState.pending().apply(AgentRunAction.START);
		assertEquals(AgentRunStatus.RUNNING, running.status());
		AgentRunState waiting = running.apply(AgentRunAction.WAIT_FOR_APPROVAL);
		assertEquals(AgentRunStatus.RUNNING, waiting.apply(AgentRunAction.RESUME).status());
		AgentRunState completed = running.apply(AgentRunAction.COMPLETE);
		assertThrows(IllegalStateException.class, () -> completed.apply(AgentRunAction.CANCEL));
		assertThrows(IllegalStateException.class, () -> waiting.apply(AgentRunAction.COMPLETE));
	}

	@Test
	void failedStepCannotBeReopened() {
		AgentStepState failed = AgentStepState.pending().apply(AgentStepAction.START)
				.apply(AgentStepAction.FAIL);
		assertThrows(IllegalStateException.class, () -> failed.apply(AgentStepAction.START));
		assertThrows(IllegalStateException.class, () -> failed.apply(AgentStepAction.COMPLETE));
	}
}
