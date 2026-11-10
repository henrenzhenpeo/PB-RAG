package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.chunking.ChunkDraft;
import dev.pbrag.ingestion.chunking.ChunkingStrategy;
import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.domain.IngestionJobAction;
import dev.pbrag.ingestion.domain.IngestionJobState;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.parser.DocumentParser;
import dev.pbrag.ingestion.parser.DocumentParserRegistry;
import dev.pbrag.ingestion.parser.ParsedDocument;
import dev.pbrag.ingestion.parser.UnsupportedDocumentTypeException;
import dev.pbrag.ingestion.storage.ObjectStorage;
import dev.pbrag.knowledge.domain.DocumentVersion;
import dev.pbrag.knowledge.mapper.DocumentVersionMapper;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 异步文档入库 Worker，串联任务领取、解析、分块、Embedding、索引完成与失败重试。
 * 仅在存在真实 EmbeddingModel 时启用，避免未配置模型时写入伪造向量。
 */
@Component
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai")
public class IngestionWorker {

	private static final Logger log = LoggerFactory.getLogger(IngestionWorker.class);
	private final IngestionJobMapper jobMapper;
	private final IngestionJobClaimService claimService;
	private final DocumentVersionMapper versionMapper;
	private final ObjectStorage storage;
	private final DocumentParserRegistry parserRegistry;
	private final ChunkingStrategy chunkingStrategy;
	private final ChunkPersistenceService chunkPersistence;
	private final EmbeddingModel embeddingModel;
	private final String workerId;
	private final long leaseSeconds;
	private final long retryDelaySeconds;
	private final int embeddingDimensions;
	private final int embeddingBatchSize;

	public IngestionWorker(
			IngestionJobMapper jobMapper,
			IngestionJobClaimService claimService,
			DocumentVersionMapper versionMapper,
			ObjectStorage storage,
			DocumentParserRegistry parserRegistry,
			ChunkingStrategy chunkingStrategy,
			ChunkPersistenceService chunkPersistence,
			EmbeddingModel embeddingModel,
			@Value("${pbrag.ingestion.worker.id:}") String configuredWorkerId,
			@Value("${pbrag.ingestion.worker.lease-seconds:300}") long leaseSeconds,
			@Value("${pbrag.ingestion.worker.retry-delay-seconds:30}") long retryDelaySeconds,
			@Value("${pbrag.ingestion.embedding.dimensions:1024}") int embeddingDimensions,
			@Value("${pbrag.ingestion.embedding.batch-size:32}") int embeddingBatchSize) {
		this.jobMapper = jobMapper;
		this.claimService = claimService;
		this.versionMapper = versionMapper;
		this.storage = storage;
		this.parserRegistry = parserRegistry;
		this.chunkingStrategy = chunkingStrategy;
		this.chunkPersistence = chunkPersistence;
		this.embeddingModel = embeddingModel;
		this.workerId = configuredWorkerId.isBlank() ? defaultWorkerId() : configuredWorkerId;
		this.leaseSeconds = leaseSeconds;
		this.retryDelaySeconds = retryDelaySeconds;
		this.embeddingDimensions = embeddingDimensions;
		if (embeddingBatchSize < 1) throw new IllegalArgumentException("Embedding batch size must be positive");
		this.embeddingBatchSize = embeddingBatchSize;
	}

	/** 每轮最多领取一个任务，缩短事务与锁持有时间，并让多个实例自然并行消费。 */
	@Scheduled(fixedDelayString = "${pbrag.ingestion.worker.poll-delay-ms:1000}")
	public void poll() {
		IngestionJob job = claimService.recoverAndClaim(workerId, leaseSeconds, retryDelaySeconds);
		if (job == null) return;
		process(job);
	}

	/**
	 * 按状态机顺序执行完整管线；每次阶段推进都校验 Worker 租约，失去所有权后立即停止。
	 * 所有异常统一转换为稳定错误码并交由数据库决定重试或最终失败。
	 */
	private void process(IngestionJob job) {
		IngestionJobState state = new IngestionJobState(job.status(), job.stage());
		try {
			DocumentVersion version = versionMapper.selectById(job.documentVersionId());
			if (version == null || !job.workspaceId().equals(version.workspaceId())) {
				throw new IllegalStateException("Document version is missing or outside the job workspace");
			}
			DocumentParser parser = parserRegistry.requiredParser(version.storageKey());
			ParsedDocument parsed;
			try (InputStream input = storage.open(version.storageKey())) {
				parsed = parser.parse(input);
			}
			versionMapper.updateParserVersion(job.workspaceId(), version.id(), parsed.parserVersion());
			state = advance(job.id(), state, IngestionJobAction.START_CHUNKING);

			List<ChunkDraft> chunks = chunkingStrategy.split(parsed);
			if (chunks.isEmpty()) throw new IllegalArgumentException("Document produced no chunks");
			chunkPersistence.replace(job.workspaceId(), version.id(), chunks, chunkingStrategy.version());
			state = advance(job.id(), state, IngestionJobAction.START_EMBEDDING);

			List<float[]> embeddings = embedInBatches(job.id(), state, chunks);
			chunkPersistence.writeEmbeddings(job.workspaceId(), version.id(), chunks, embeddings, embeddingDimensions);
			state = advance(job.id(), state, IngestionJobAction.START_INDEXING);

			state.apply(IngestionJobAction.COMPLETE);
			if (jobMapper.complete(job.id(), workerId) != 1) {
				throw new IllegalStateException("Job lease was lost before completion");
			}
			log.info("ingestion_job_completed workspaceId={} documentVersionId={} jobId={} chunks={}",
					job.workspaceId(), version.id(), job.id(), chunks.size());
		}
		catch (Exception exception) {
			String code = errorCode(exception);
			String message = safeMessage(exception);
			IngestionJobAction failureAction = job.attemptCount() < job.maxAttempts()
					? IngestionJobAction.WAIT_FOR_RETRY : IngestionJobAction.FAIL;
			state.apply(failureAction);
			jobMapper.failOrRetry(job.id(), workerId, code, message, state.stage(), retryDelaySeconds);
			log.warn("ingestion_job_failed workspaceId={} documentVersionId={} jobId={} errorCode={} message={}",
					job.workspaceId(), job.documentVersionId(), job.id(), code, message);
		}
	}

	/** 分批调用模型以控制请求大小，并在每批前续租，避免长耗时模型调用导致任务被重复领取。 */
	private List<float[]> embedInBatches(
			UUID jobId, IngestionJobState state, List<ChunkDraft> chunks) {
		List<float[]> embeddings = new ArrayList<>(chunks.size());
		for (int start = 0; start < chunks.size(); start += embeddingBatchSize) {
			if (jobMapper.renewLease(jobId, workerId, state.stage(), leaseSeconds) != 1) {
				throw new IllegalStateException("Job lease was lost during embedding");
			}
			int end = Math.min(chunks.size(), start + embeddingBatchSize);
			embeddings.addAll(embeddingModel.embed(
					chunks.subList(start, end).stream().map(ChunkDraft::content).toList()));
		}
		return embeddings;
	}

	private IngestionJobState advance(
			UUID jobId, IngestionJobState current, IngestionJobAction action) {
		IngestionJobState next = current.apply(action);
		if (jobMapper.advanceStage(jobId, workerId, current.stage(), next.stage(), leaseSeconds) != 1) {
			throw new IllegalStateException("Job lease was lost while advancing to " + next.stage());
		}
		return next;
	}

	private static String errorCode(Exception exception) {
		if (exception instanceof UnsupportedDocumentTypeException) return "UNSUPPORTED_DOCUMENT_TYPE";
		if (exception instanceof EmbeddingDimensionMismatchException) return "EMBEDDING_DIMENSION_MISMATCH";
		if (exception instanceof java.io.IOException) return "DOCUMENT_READ_FAILED";
		if (exception instanceof IllegalArgumentException) return "DOCUMENT_PARSE_FAILED";
		return "INGESTION_FAILED";
	}

	/** 限制持久化错误文本长度，避免第三方异常撑大任务表或意外写入大段文档内容。 */
	private static String safeMessage(Exception exception) {
		String value = exception.getMessage();
		if (value == null || value.isBlank()) value = exception.getClass().getSimpleName();
		return value.length() > 500 ? value.substring(0, 500) : value;
	}

	private static String defaultWorkerId() {
		try {
			return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
		}
		catch (Exception ignored) {
			return "worker-" + UUID.randomUUID().toString().substring(0, 8);
		}
	}
}
