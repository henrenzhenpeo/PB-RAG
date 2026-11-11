package dev.pbrag.chat.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** 运行时取消注册表的信号发送、部分正文读取和资源释放测试。 */
class RagCancellationRegistryTests {

	@Test
	void signalsActiveGenerationAndReleasesIt() {
		RagCancellationRegistry registry = new RagCancellationRegistry();
		UUID messageId = UUID.randomUUID();
		StringBuffer answer = new StringBuffer("partial");
		RagCancellationRegistry.CancellationHandle handle =
				registry.register(messageId, answer::toString);
		AtomicBoolean cancelled = new AtomicBoolean();
		handle.cancellation().subscribe(ignored -> { }, ignored -> { }, () -> cancelled.set(true));

		assertThat(registry.partialAnswer(messageId)).isEqualTo("partial");
		assertThat(registry.cancel(messageId)).isTrue();
		assertThat(cancelled).isTrue();

		registry.release(handle);
		assertThat(registry.partialAnswer(messageId)).isEmpty();
		assertThat(registry.cancel(messageId)).isFalse();
	}
}
