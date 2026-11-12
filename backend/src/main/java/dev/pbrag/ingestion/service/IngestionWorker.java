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
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

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
	private final IngestionCompletionService completionService;
	private final IngestionSignalService signalService;
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
			IngestionCompletionService completionService,
			IngestionSignalService signalService,
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
		this.completionService = completionService;
		this.signalService = signalService;
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
	 * 异常先转换为稳定错误码和可重试性，再由带租约条件的数据库更新决定是否真正落库。
	 */
	private void process(IngestionJob job) {
		IngestionJobState state = new IngestionJobState(job.status(), job.stage());
		try {
			ensureNotCancelled(job.id());
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
			ensureNotCancelled(job.id());
			state = advance(job.id(), state, IngestionJobAction.START_CHUNKING);

			List<ChunkDraft> chunks = chunkingStrategy.split(parsed);
			if (chunks.isEmpty()) throw new IllegalArgumentException("Document produced no chunks");
			ensureNotCancelled(job.id());
			if (jobMapper.renewLease(job.id(), workerId, state.stage(), leaseSeconds) != 1) {
				throw new IllegalStateException("Job lease was lost before chunk persistence");
			}
			chunkPersistence.replace(job.workspaceId(), version.id(), chunks, chunkingStrategy.version());
			ensureNotCancelled(job.id());
			state = advance(job.id(), state, IngestionJobAction.START_EMBEDDING);

			List<float[]> embeddings = embedInBatches(job.id(), state, chunks);
			chunkPersistence.writeEmbeddings(job.workspaceId(), version.id(), chunks, embeddings, embeddingDimensions);
			state = advance(job.id(), state, IngestionJobAction.START_INDEXING);

			state.apply(IngestionJobAction.COMPLETE);
			completionService.complete(job.id(), workerId, job.workspaceId(), version.documentId(), version.id());
			log.info("ingestion_job_completed workspaceId={} documentVersionId={} jobId={} chunks={}",
					job.workspaceId(), version.id(), job.id(), chunks.size());
		}
		catch (Exception exception) {
			if (exception instanceof IngestionCancelledException) {
				log.info("ingestion_job_cancelled workspaceId={} documentVersionId={} jobId={}",
						job.workspaceId(), job.documentVersionId(), job.id());
				return;
			}
			IngestionFailure failure = classify(exception);
			String code = failure.code();
			String message = safeMessage(exception);
			boolean retry = failure.retryable() && job.attemptCount() < job.maxAttempts();
			IngestionJobAction failureAction = retry
					? IngestionJobAction.WAIT_FOR_RETRY : IngestionJobAction.FAIL;
			state.apply(failureAction);
			int updated = jobMapper.failOrRetry(
					job.id(), workerId, code, message, state.stage(), failure.retryable(), retryDelaySeconds);
			if (updated != 1) {
				log.warn("ingestion_job_failure_not_persisted workspaceId={} documentVersionId={} jobId={} errorCode={} reason=lease_lost",
						job.workspaceId(), job.documentVersionId(), job.id(), code);
				return;
			}
			log.warn("{} workspaceId={} documentVersionId={} jobId={} errorCode={} message={}",
					retry ? "ingestion_job_retry_scheduled" : "ingestion_job_failed",
					job.workspaceId(), job.documentVersionId(), job.id(), code, message);
		}
	}

	/** 分批调用模型以控制请求大小，并在每批前续租，避免长耗时模型调用导致任务被重复领取。 */
	private List<float[]> embedInBatches(
			UUID jobId, IngestionJobState state, List<ChunkDraft> chunks) {
		List<float[]> embeddings = new ArrayList<>(chunks.size());
		for (int start = 0; start < chunks.size(); start += embeddingBatchSize) {
			ensureNotCancelled(jobId);
			if (jobMapper.renewLease(jobId, workerId, state.stage(), leaseSeconds) != 1) {
				throw new IllegalStateException("Job lease was lost during embedding");
			}
			int end = Math.min(chunks.size(), start + embeddingBatchSize);
			embeddings.addAll(embeddingModel.embed(
					chunks.subList(start, end).stream().map(ChunkDraft::content).toList()));
		}
		return embeddings;
	}

	private void ensureNotCancelled(UUID jobId) {
		if (signalService.cancellationRequested(jobId)) throw new IngestionCancelledException();
	}

	private static final class IngestionCancelledException extends RuntimeException { }

	private IngestionJobState advance(
			UUID jobId, IngestionJobState current, IngestionJobAction action) {
		IngestionJobState next = current.apply(action);
		if (jobMapper.advanceStage(jobId, workerId, current.stage(), next.stage(), leaseSeconds) != 1) {
			throw new IllegalStateException("Job lease was lost while advancing to " + next.stage());
		}
		return next;
	}

	static IngestionFailure classify(Exception exception) {
		if (exception instanceof UnsupportedDocumentTypeException) {
			return new IngestionFailure("UNSUPPORTED_DOCUMENT_TYPE", false);
		}
		if (exception instanceof EmbeddingDimensionMismatchException) {
			return new IngestionFailure("EMBEDDING_DIMENSION_MISMATCH", false);
		}
		if (exception instanceof IllegalArgumentException) {
			return new IngestionFailure("DOCUMENT_PARSE_FAILED", false);
		}
		for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
			if (cause instanceof HttpStatusCodeException statusException) {
				boolean retryable = statusException.getStatusCode().value() == 429
						|| statusException.getStatusCode().is5xxServerError();
				return new IngestionFailure(
						retryable ? "EMBEDDING_PROVIDER_UNAVAILABLE" : "EMBEDDING_REQUEST_REJECTED",
						retryable);
			}
			if (cause instanceof ResourceAccessException
					|| cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
				return new IngestionFailure("EMBEDDING_PROVIDER_UNAVAILABLE", true);
			}
		}
		if (exception instanceof IOException) return new IngestionFailure("DOCUMENT_READ_FAILED", true);
		return new IngestionFailure("INGESTION_FAILED", true);
	}

	/** Worker 可持久化的稳定错误分类；retryable 不依赖自然语言消息。 */
	record IngestionFailure(String code, boolean retryable) { }

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
