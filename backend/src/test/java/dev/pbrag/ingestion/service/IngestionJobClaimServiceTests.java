package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionJobStatus;
import dev.pbrag.ingestion.domain.IngestionStage;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionJobClaimServiceTests {
	private final IngestionJobMapper jobMapper = mock(IngestionJobMapper.class);
	private final IngestionJobClaimService service = new IngestionJobClaimService(jobMapper);

	@Test
	void recoversRetriesAndAcceptsDomainValidatedClaim() throws Exception {
		IngestionJob claimed = job(IngestionJobStatus.RUNNING, IngestionStage.PARSING);
		when(jobMapper.claimNext("worker-1", 300)).thenReturn(claimed);

		assertThat(service.recoverAndClaim("worker-1", 300, 30)).isSameAs(claimed);
		verify(jobMapper).recoverExpired(30);
		verify(jobMapper).makeRetriesAvailable();
	}

	@Test
	void rejectsClaimThatBypassesStartParsingTransition() throws Exception {
		when(jobMapper.claimNext("worker-1", 300))
				.thenReturn(job(IngestionJobStatus.RUNNING, IngestionStage.EMBEDDING));

		assertThatThrownBy(() -> service.recoverAndClaim("worker-1", 300, 30))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("START_PARSING");
	}

	private static IngestionJob job(IngestionJobStatus status, IngestionStage stage) throws Exception {
		IngestionJob job = new IngestionJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
		set(job, "status", status);
		set(job, "stage", stage);
		return job;
	}

	private static void set(IngestionJob job, String fieldName, Object value) throws Exception {
		Field field = IngestionJob.class.getDeclaredField(fieldName);
		field.setAccessible(true);
		field.set(job, value);
	}
}
