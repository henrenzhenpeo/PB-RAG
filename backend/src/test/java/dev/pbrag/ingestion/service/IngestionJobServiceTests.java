package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionJobStatus;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.mapper.ChunkMapper;
import dev.pbrag.ingestion.service.impl.IngestionJobServiceImpl;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IngestionJobServiceTests {
	@Test
	void persistsCancellationBeforePublishingTheFastSignal() {
		UUID workspaceId = UUID.randomUUID();
		UUID jobId = UUID.randomUUID();
		IngestionJob running = mock(IngestionJob.class);
		IngestionJob cancelled = mock(IngestionJob.class);
		when(running.status()).thenReturn(IngestionJobStatus.RUNNING);
		UUID versionId = UUID.randomUUID();
		when(running.documentVersionId()).thenReturn(versionId);
		when(cancelled.status()).thenReturn(IngestionJobStatus.CANCELLED);
		IngestionJobMapper mapper = mock(IngestionJobMapper.class);
		when(mapper.selectByWorkspaceIdAndId(workspaceId, jobId)).thenReturn(running, cancelled);
		when(mapper.cancel(workspaceId, jobId)).thenReturn(1);
		IngestionSignalService signals = mock(IngestionSignalService.class);
		ChunkMapper chunks = mock(ChunkMapper.class);
		IngestionJobService service = new IngestionJobServiceImpl(mapper, chunks, signals);

		IngestionJob result = service.cancel(workspaceId, jobId);

		assertThat(result.status()).isEqualTo(IngestionJobStatus.CANCELLED);
		verify(mapper).cancel(workspaceId, jobId);
		verify(signals).markCancelled(jobId);
		verify(chunks).deleteByDocumentVersion(workspaceId, versionId);
	}
}
