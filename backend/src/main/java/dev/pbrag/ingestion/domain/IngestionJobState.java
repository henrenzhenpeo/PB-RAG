package dev.pbrag.ingestion.domain;

import java.util.EnumSet;

/**
 * 入库任务的领域状态机。所有阶段推进都必须经过 apply，禁止服务层直接拼接下一状态。
 */
public record IngestionJobState(IngestionJobStatus status, IngestionStage stage) {

	public IngestionJobState {
		if (status == null || stage == null) {
			throw new IllegalArgumentException("Job status and stage are required");
		}
	}

	public static IngestionJobState pending() {
		return new IngestionJobState(IngestionJobStatus.PENDING, IngestionStage.PENDING);
	}

	public IngestionJobState apply(IngestionJobAction action) {
		return switch (action) {
			case START_PARSING -> require(
					status == IngestionJobStatus.PENDING && stage == IngestionStage.PENDING,
					new IngestionJobState(IngestionJobStatus.RUNNING, IngestionStage.PARSING), action);
			case START_CHUNKING -> requireRunningStage(
					IngestionStage.PARSING, IngestionStage.CHUNKING, action);
			case START_EMBEDDING -> requireRunningStage(
					IngestionStage.CHUNKING, IngestionStage.EMBEDDING, action);
			case START_INDEXING -> requireRunningStage(
					IngestionStage.EMBEDDING, IngestionStage.INDEXING, action);
			case COMPLETE -> require(
					status == IngestionJobStatus.RUNNING && stage == IngestionStage.INDEXING,
					new IngestionJobState(IngestionJobStatus.COMPLETED, IngestionStage.COMPLETED), action);
			case WAIT_FOR_RETRY -> require(
					status == IngestionJobStatus.RUNNING && retryableStage(stage),
					new IngestionJobState(IngestionJobStatus.RETRY_WAIT, stage), action);
			case FAIL -> require(
					EnumSet.of(IngestionJobStatus.RUNNING, IngestionJobStatus.RETRY_WAIT).contains(status),
					new IngestionJobState(IngestionJobStatus.FAILED, stage), action);
			case RETRY -> require(
					EnumSet.of(IngestionJobStatus.RETRY_WAIT, IngestionJobStatus.FAILED).contains(status),
					pending(), action);
			case CANCEL -> require(
					!EnumSet.of(IngestionJobStatus.COMPLETED, IngestionJobStatus.CANCELLED).contains(status),
					new IngestionJobState(IngestionJobStatus.CANCELLED, stage), action);
		};
	}

	private IngestionJobState requireRunningStage(
			IngestionStage expected,
			IngestionStage next,
			IngestionJobAction action) {
		return require(
				status == IngestionJobStatus.RUNNING && stage == expected,
				new IngestionJobState(IngestionJobStatus.RUNNING, next), action);
	}

	private IngestionJobState require(boolean condition, IngestionJobState next, IngestionJobAction action) {
		if (!condition) {
			throw new IllegalStateException(
					"Action %s is not allowed from %s/%s".formatted(action, status, stage));
		}
		return next;
	}

	private static boolean retryableStage(IngestionStage stage) {
		return EnumSet.of(
				IngestionStage.PARSING,
				IngestionStage.CHUNKING,
				IngestionStage.EMBEDDING,
				IngestionStage.INDEXING).contains(stage);
	}
}
