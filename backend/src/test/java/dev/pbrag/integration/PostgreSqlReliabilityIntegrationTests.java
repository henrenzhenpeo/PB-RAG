package dev.pbrag.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionJobStatus;
import dev.pbrag.ingestion.domain.IngestionStage;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.service.IngestionCompletionService;
import dev.pbrag.ingestion.service.IngestionJobClaimService;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.mapper.ChunkRetrievalMapper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 在真实 PostgreSQL 18、pgvector 和 ParadeDB pg_search 上验证入库可靠性及资源作用域。
 * 测试使用隔离容器执行完整 Flyway 迁移，避免 H2 或 Mock 掩盖锁、租约和扩展 SQL 差异。
 */
@Testcontainers
@SpringBootTest(properties = {
		"spring.profiles.active=test",
		"spring.ai.model.chat=none",
		"spring.ai.model.embedding=none",
		"spring.ai.vectorstore.type=none",
		"pbrag.ingestion.redis-signals.enabled=false"
})
class PostgreSqlReliabilityIntegrationTests {

	private static final DockerImageName PARADE_DB_IMAGE = DockerImageName
			.parse("paradedb/paradedb:0.26.6-pg18")
			.asCompatibleSubstituteFor("postgres");

	@Container
	private static final PostgreSQLContainer postgres = new PostgreSQLContainer(PARADE_DB_IMAGE)
			.withDatabaseName("pbrag_test")
			.withUsername("pbrag")
			.withPassword("pbrag");

	@DynamicPropertySource
	static void databaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);
	}

	@Autowired private JdbcTemplate jdbc;
	@Autowired private IngestionJobMapper jobMapper;
	@Autowired private IngestionJobClaimService claimService;
	@Autowired private IngestionCompletionService completionService;
	@Autowired private ChunkRetrievalMapper retrievalMapper;

	@BeforeEach
	void cleanBusinessData() {
		jdbc.execute("TRUNCATE TABLE workspaces CASCADE");
	}

	@Test
	void concurrentWorkersClaimOneJobOnlyOnce() throws Exception {
		Fixture fixture = fixture("concurrent", false);
		UUID jobId = insertJob(fixture.workspaceId(), fixture.currentVersionId());
		CyclicBarrier start = new CyclicBarrier(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<IngestionJob> first = executor.submit(() -> claimAfter(start, "worker-a"));
			Future<IngestionJob> second = executor.submit(() -> claimAfter(start, "worker-b"));
			List<IngestionJob> claimed = Stream.of(first.get(), second.get())
					.filter(java.util.Objects::nonNull).toList();

			assertThat(claimed).singleElement().extracting(IngestionJob::id).isEqualTo(jobId);
			assertThat(jobMapper.selectByWorkspaceIdAndId(fixture.workspaceId(), jobId).attemptCount())
					.isEqualTo(1);
		}
		finally {
			executor.shutdownNow();
		}
	}

	@Test
	void expiredWorkerLeaseIsRecoveredAndReclaimed() {
		Fixture fixture = fixture("lease", false);
		UUID jobId = insertJob(fixture.workspaceId(), fixture.currentVersionId());
		assertThat(claimService.recoverAndClaim("crashed-worker", 300, 0)).isNotNull();
		jdbc.update("UPDATE ingestion_jobs SET lease_expires_at = CURRENT_TIMESTAMP - INTERVAL '1 second' WHERE id = ?",
				jobId);

		IngestionJob reclaimed = claimService.recoverAndClaim("recovery-worker", 300, 0);

		assertThat(reclaimed).isNotNull();
		assertThat(reclaimed.id()).isEqualTo(jobId);
		assertThat(reclaimed.attemptCount()).isEqualTo(2);
		assertThat(reclaimed.getLeaseOwner()).isEqualTo("recovery-worker");
		assertThat(reclaimed.errorCode()).isNull();
		assertThat(jobMapper.renewLease(jobId, "crashed-worker", IngestionStage.PARSING, 300)).isZero();
		assertThat(jobMapper.advanceStage(jobId, "crashed-worker", IngestionStage.PARSING,
				IngestionStage.CHUNKING, 300)).isZero();
		assertThat(jobMapper.complete(jobId, "crashed-worker")).isZero();
	}

	@Test
	void completionSwitchesCurrentVersionInTheSameTransaction() {
		Fixture fixture = fixture("completion", true);
		UUID jobId = insertJob(fixture.workspaceId(), fixture.currentVersionId());
		claimAndAdvanceToIndexing(jobId, "worker-complete");

		completionService.complete(jobId, "worker-complete", fixture.workspaceId(),
				fixture.documentId(), fixture.currentVersionId());

		assertThat(currentVersion(fixture.documentId())).isEqualTo(fixture.currentVersionId());
		assertThat(jobMapper.selectByWorkspaceIdAndId(fixture.workspaceId(), jobId).status())
				.isEqualTo(IngestionJobStatus.COMPLETED);
	}

	@Test
	void lostLeaseRollsBackCurrentVersionSwitch() {
		Fixture fixture = fixture("rollback", true);
		UUID jobId = insertJob(fixture.workspaceId(), fixture.currentVersionId());
		claimAndAdvanceToIndexing(jobId, "lease-owner");

		assertThatThrownBy(() -> completionService.complete(jobId, "wrong-worker", fixture.workspaceId(),
				fixture.documentId(), fixture.currentVersionId()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("lease was lost");

		assertThat(currentVersion(fixture.documentId())).isEqualTo(fixture.previousVersionId());
		IngestionJob unchanged = jobMapper.selectByWorkspaceIdAndId(fixture.workspaceId(), jobId);
		assertThat(unchanged.status()).isEqualTo(IngestionJobStatus.RUNNING);
		assertThat(unchanged.stage()).isEqualTo(IngestionStage.INDEXING);
	}

	@Test
	void bm25ReturnsCurrentVersionInsideRequestedResourceScopeOnly() {
		Fixture requested = fixture("requested", true);
		Fixture otherWorkspace = fixture("other", false);
		insertChunk(requested.workspaceId(), requested.previousVersionId(), "paymentsentinel old version");
		UUID expectedChunk = insertChunk(requested.workspaceId(), requested.currentVersionId(),
				"paymentsentinel current version");
		jdbc.update("UPDATE documents SET current_version_id = ? WHERE id = ?",
				requested.currentVersionId(), requested.documentId());
		insertChunk(otherWorkspace.workspaceId(), otherWorkspace.currentVersionId(),
				"paymentsentinel outside workspace");

		List<RetrievalHit> hits = retrievalMapper.searchBm25CurrentVersions(
				requested.workspaceId(), requested.knowledgeBaseId(), "paymentsentinel", 30);

		assertThat(hits).extracting(RetrievalHit::chunkId).containsExactly(expectedChunk);
		assertThat(retrievalMapper.searchBm25CurrentVersions(
				requested.workspaceId(), otherWorkspace.knowledgeBaseId(), "paymentsentinel", 30)).isEmpty();
	}

	@Test
	void compositeForeignKeyRejectsCrossWorkspaceChunk() {
		Fixture source = fixture("source", false);
		Fixture wrongScope = fixture("wrong", false);

		assertThatThrownBy(() -> insertChunk(source.workspaceId(), wrongScope.currentVersionId(), "invalid scope"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private IngestionJob claimAfter(CyclicBarrier start, String workerId) throws Exception {
		start.await();
		return claimService.recoverAndClaim(workerId, 300, 30);
	}

	private void claimAndAdvanceToIndexing(UUID jobId, String workerId) {
		IngestionJob claimed = claimService.recoverAndClaim(workerId, 300, 30);
		assertThat(claimed).isNotNull();
		assertThat(claimed.id()).isEqualTo(jobId);
		assertThat(jobMapper.advanceStage(jobId, workerId, IngestionStage.PARSING,
				IngestionStage.CHUNKING, 300)).isEqualTo(1);
		assertThat(jobMapper.advanceStage(jobId, workerId, IngestionStage.CHUNKING,
				IngestionStage.EMBEDDING, 300)).isEqualTo(1);
		assertThat(jobMapper.advanceStage(jobId, workerId, IngestionStage.EMBEDDING,
				IngestionStage.INDEXING, 300)).isEqualTo(1);
	}

	private Fixture fixture(String suffix, boolean withPreviousVersion) {
		UUID workspaceId = UUID.randomUUID();
		UUID knowledgeBaseId = UUID.randomUUID();
		UUID documentId = UUID.randomUUID();
		UUID previousVersionId = withPreviousVersion ? UUID.randomUUID() : null;
		UUID currentVersionId = UUID.randomUUID();
		jdbc.update("INSERT INTO workspaces (id, name) VALUES (?, ?)", workspaceId, "workspace-" + suffix);
		jdbc.update("INSERT INTO knowledge_bases (id, workspace_id, name) VALUES (?, ?, ?)",
				knowledgeBaseId, workspaceId, "kb-" + suffix);
		jdbc.update("INSERT INTO documents (id, workspace_id, knowledge_base_id, display_name, media_type) "
				+ "VALUES (?, ?, ?, ?, 'text/markdown')", documentId, workspaceId, knowledgeBaseId,
				"document-" + suffix + ".md");
		if (previousVersionId != null) {
			insertVersion(previousVersionId, workspaceId, documentId, 1, 'a', suffix + "-v1");
		}
		insertVersion(currentVersionId, workspaceId, documentId,
				previousVersionId == null ? 1 : 2, 'b', suffix + "-current");
		UUID selectedVersion = previousVersionId == null ? currentVersionId : previousVersionId;
		jdbc.update("UPDATE documents SET current_version_id = ? WHERE id = ?", selectedVersion, documentId);
		return new Fixture(workspaceId, knowledgeBaseId, documentId, previousVersionId, currentVersionId);
	}

	private void insertVersion(UUID id, UUID workspaceId, UUID documentId, int number, char hashCharacter,
			String storageKey) {
		jdbc.update("INSERT INTO document_versions "
				+ "(id, workspace_id, document_id, version_number, content_sha256, storage_key) "
				+ "VALUES (?, ?, ?, ?, ?, ?)", id, workspaceId, documentId, number,
				String.valueOf(hashCharacter).repeat(64), storageKey);
	}

	private UUID insertJob(UUID workspaceId, UUID versionId) {
		UUID jobId = UUID.randomUUID();
		jdbc.update("INSERT INTO ingestion_jobs (id, workspace_id, document_version_id, status, stage) "
				+ "VALUES (?, ?, ?, 'PENDING', 'PENDING')", jobId, workspaceId, versionId);
		return jobId;
	}

	private UUID insertChunk(UUID workspaceId, UUID versionId, String content) {
		UUID chunkId = UUID.randomUUID();
		jdbc.update("INSERT INTO chunks "
				+ "(id, workspace_id, document_version_id, sequence_number, content, source_position) "
				+ "VALUES (?, ?, ?, 0, ?, '{}'::jsonb)", chunkId, workspaceId, versionId, content);
		return chunkId;
	}

	private UUID currentVersion(UUID documentId) {
		return jdbc.queryForObject("SELECT current_version_id FROM documents WHERE id = ?", UUID.class, documentId);
	}

	private record Fixture(UUID workspaceId, UUID knowledgeBaseId, UUID documentId,
			UUID previousVersionId, UUID currentVersionId) { }
}
