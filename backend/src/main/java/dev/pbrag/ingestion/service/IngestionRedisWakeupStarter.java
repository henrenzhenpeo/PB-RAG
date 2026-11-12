package dev.pbrag.ingestion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 在后台建立并恢复 Redis Pub/Sub 订阅，连接失败不能阻断主应用启动。 */
@Component
@ConditionalOnBean(IngestionWorker.class)
@ConditionalOnProperty(name = "pbrag.ingestion.redis-signals.enabled", havingValue = "true")
public class IngestionRedisWakeupStarter {
	private static final Logger log = LoggerFactory.getLogger(IngestionRedisWakeupStarter.class);
	private final RedisMessageListenerContainer container;

	/** 注入已关闭自动启动的监听容器。 */
	public IngestionRedisWakeupStarter(RedisMessageListenerContainer container) {
		this.container = container;
	}

	/** Redis 不可用时按固定间隔重连；期间 PostgreSQL Worker 轮询继续处理任务。 */
	@Scheduled(
			initialDelayString = "${pbrag.ingestion.redis-signals.initial-delay-ms:1000}",
			fixedDelayString = "${pbrag.ingestion.redis-signals.reconnect-delay-ms:10000}")
	public void ensureStarted() {
		if (container.isRunning()) return;
		try {
			container.start();
			log.info("ingestion_redis_wakeup_listener_started");
		}
		catch (RuntimeException exception) {
			log.warn("ingestion_redis_wakeup_listener_unavailable errorType={}",
					exception.getClass().getSimpleName());
		}
	}
}
