package dev.pbrag.chat.service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 管理当前进程内正在生成的 RAG 消息，使显式停止请求能够主动终止对应模型流。 */
@Component
public class RagCancellationRegistry {

	private final ConcurrentMap<UUID, ActiveGeneration> active = new ConcurrentHashMap<>();

	/** 为一条助手消息注册唯一取消信号；重复注册表示运行时状态异常。 */
	public CancellationHandle register(UUID messageId, Supplier<String> partialAnswer) {
		ActiveGeneration generation = new ActiveGeneration(Sinks.empty(), partialAnswer);
		if (active.putIfAbsent(messageId, generation) != null) {
			throw new IllegalStateException("RAG message is already active");
		}
		return new CancellationHandle(messageId, generation);
	}

	/** 向当前进程中的生成流发送停止信号；消息不在本实例运行时返回 false。 */
	public boolean cancel(UUID messageId) {
		ActiveGeneration generation = active.get(messageId);
		return generation != null && generation.signal().tryEmitEmpty().isSuccess();
	}

	/** 读取服务端已经接收的回答增量；跨实例或尚未注册时返回空正文。 */
	public String partialAnswer(UUID messageId) {
		ActiveGeneration generation = active.get(messageId);
		return generation == null ? "" : generation.partialAnswer().get();
	}

	/** 在流进入终态后按实例精确移除信号，避免误删后来注册的新任务。 */
	public void release(CancellationHandle handle) {
		active.remove(handle.messageId, handle.generation);
	}

	/** 一次生成对应的取消句柄，只向 RAG 编排层暴露只读完成信号。 */
	public static final class CancellationHandle {
		private final UUID messageId;
		private final ActiveGeneration generation;

		/** 只允许注册表创建与内部运行状态绑定的句柄。 */
		private CancellationHandle(UUID messageId, ActiveGeneration generation) {
			this.messageId = messageId;
			this.generation = generation;
		}

		/** 返回在用户显式停止时完成的 Reactor 信号。 */
		public Mono<Void> cancellation() {
			return generation.signal().asMono();
		}
	}

	/** 当前生成流的停止信号与部分正文读取器。 */
	private record ActiveGeneration(Sinks.Empty<Void> signal, Supplier<String> partialAnswer) {
	}
}
