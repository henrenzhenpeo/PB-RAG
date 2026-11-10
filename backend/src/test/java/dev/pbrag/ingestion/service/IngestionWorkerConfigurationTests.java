package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/** 防止 Worker 再次依赖自动配置 Bean 的注册顺序而被静默跳过。 */
class IngestionWorkerConfigurationTests {

	@Test
	void workerIsEnabledByExplicitEmbeddingConfiguration() {
		ConditionalOnProperty condition = IngestionWorker.class.getAnnotation(ConditionalOnProperty.class);

		assertThat(condition).isNotNull();
		assertThat(condition.name()).containsExactly("spring.ai.model.embedding");
		assertThat(condition.havingValue()).isEqualTo("openai");
	}
}
