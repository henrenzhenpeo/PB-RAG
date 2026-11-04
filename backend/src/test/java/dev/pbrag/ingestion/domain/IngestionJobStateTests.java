package dev.pbrag.ingestion.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class IngestionJobStateTests {

	@Test
	void completesOnlyThroughOrderedStages() {
		IngestionJobState completed = IngestionJobState.pending()
				.apply(IngestionJobAction.START_PARSING)
				.apply(IngestionJobAction.START_CHUNKING)
				.apply(IngestionJobAction.START_EMBEDDING)
				.apply(IngestionJobAction.START_INDEXING)
				.apply(IngestionJobAction.COMPLETE);

		assertThat(completed.status()).isEqualTo(IngestionJobStatus.COMPLETED);
		assertThat(completed.stage()).isEqualTo(IngestionStage.COMPLETED);
	}

	@Test
	void rejectsSkippedStage() {
		IngestionJobState parsing = IngestionJobState.pending()
				.apply(IngestionJobAction.START_PARSING);

		assertThatIllegalStateException()
				.isThrownBy(() -> parsing.apply(IngestionJobAction.START_EMBEDDING))
				.withMessageContaining("START_EMBEDDING");
	}

	@Test
	void retryAlwaysReturnsToPendingParsingBoundary() {
		IngestionJobState retry = IngestionJobState.pending()
				.apply(IngestionJobAction.START_PARSING)
				.apply(IngestionJobAction.START_CHUNKING)
				.apply(IngestionJobAction.WAIT_FOR_RETRY)
				.apply(IngestionJobAction.RETRY);

		assertThat(retry).isEqualTo(IngestionJobState.pending());
	}

	@Test
	void completedJobCannotBeCancelled() {
		IngestionJobState completed = new IngestionJobState(
				IngestionJobStatus.COMPLETED,
				IngestionStage.COMPLETED);

		assertThatIllegalStateException()
				.isThrownBy(() -> completed.apply(IngestionJobAction.CANCEL));
	}
}
