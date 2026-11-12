package dev.pbrag.ingestion.service;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Redis 入库信号适配器；只负责低延迟唤醒和取消传播，不保存任务事实状态。 */
@Service
public class IngestionSignalService {
	public static final String WAKEUP_CHANNEL = "pbrag:ingestion:wakeup";
	private static final String CANCEL_PREFIX = "pbrag:ingestion:cancel:";
	private static final Duration CANCEL_TTL = Duration.ofHours(1);
	private static final Logger log = LoggerFactory.getLogger(IngestionSignalService.class);
	private final StringRedisTemplate redis;

	/** 注入字符串 Redis 客户端。 */
	public IngestionSignalService(StringRedisTemplate redis) {
		this.redis = redis;
	}

	/** 最佳努力发布任务可领取信号；失败时定时 PostgreSQL 轮询仍可兜底。 */
	public void notifyJobAvailable(UUID jobId) {
		try {
			redis.convertAndSend(WAKEUP_CHANNEL, jobId.toString());
		}
		catch (RuntimeException exception) {
			log.warn("ingestion_wakeup_publish_failed jobId={} errorType={}", jobId, exception.getClass().getSimpleName());
		}
	}

	/** 写入有界 TTL 的取消标记，让持有任务的实例无需等待数据库阶段更新即可停止。 */
	public void markCancelled(UUID jobId) {
		try {
			redis.opsForValue().set(CANCEL_PREFIX + jobId, "1", CANCEL_TTL);
		}
		catch (RuntimeException exception) {
			log.warn("ingestion_cancel_signal_failed jobId={} errorType={}", jobId, exception.getClass().getSimpleName());
		}
	}

	/** Redis 不可用时返回 false，由 PostgreSQL 条件更新继续保证正确性。 */
	public boolean cancellationRequested(UUID jobId) {
		try {
			return Boolean.TRUE.equals(redis.hasKey(CANCEL_PREFIX + jobId));
		}
		catch (RuntimeException exception) {
			return false;
		}
	}
}
