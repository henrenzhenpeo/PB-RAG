package dev.pbrag.ingestion.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 订阅 Redis 入库唤醒频道；收到信号后仍通过 PostgreSQL 原子领取任务。 */
@Configuration
@ConditionalOnBean(IngestionWorker.class)
@ConditionalOnProperty(name = "pbrag.ingestion.redis-signals.enabled", havingValue = "true")
public class IngestionRedisWakeupConfiguration {
	/** 每个实例收到广播后尝试领取一个任务，SKIP LOCKED 防止重复执行。 */
	@Bean
	RedisMessageListenerContainer ingestionWakeupListener(
			RedisConnectionFactory connectionFactory, IngestionWorker worker) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		// Redis 是加速通道而非启动依赖，连接由后台重试器在上下文完成后建立。
		container.setAutoStartup(false);
		container.addMessageListener((message, pattern) -> worker.poll(),
				new ChannelTopic(IngestionSignalService.WAKEUP_CHANNEL));
		return container;
	}
}
