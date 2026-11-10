package dev.pbrag.ingestion.service;

import dev.pbrag.ingestion.chunking.ChunkDraft;
import dev.pbrag.ingestion.mapper.ChunkMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chunk 与向量的事务持久化边界。
 * 分块结果按文档版本整体替换，避免重试留下重复 Chunk；向量写入前严格校验数量和维度。
 */
@Service
public class ChunkPersistenceService {

	private final ChunkMapper chunkMapper;

	public ChunkPersistenceService(ChunkMapper chunkMapper) {
		this.chunkMapper = chunkMapper;
	}

	/** 先删除旧结果再写入确定性序号，新旧结果在同一事务内对外不可见。 */
	@Transactional
	public void replace(
			UUID workspaceId, UUID documentVersionId,
			List<ChunkDraft> chunks, String chunkingVersion) {
		chunkMapper.deleteByDocumentVersion(workspaceId, documentVersionId);
		for (ChunkDraft chunk : chunks) {
			if (chunkMapper.insert(UUID.randomUUID(), workspaceId, documentVersionId, chunk, chunkingVersion) != 1) {
				throw new IllegalStateException("Failed to persist chunk " + chunk.sequenceNumber());
			}
		}
	}

	/** 任意向量缺失、维度错误或更新失败都会回滚整批写入。 */
	@Transactional
	public void writeEmbeddings(
			UUID workspaceId, UUID documentVersionId,
			List<ChunkDraft> chunks, List<float[]> embeddings, int expectedDimensions) {
		if (chunks.size() != embeddings.size()) {
			throw new IllegalStateException("Embedding result count does not match chunk count");
		}
		for (int index = 0; index < chunks.size(); index++) {
			float[] embedding = embeddings.get(index);
			if (embedding.length != expectedDimensions) {
				throw new EmbeddingDimensionMismatchException(expectedDimensions, embedding.length);
			}
			if (chunkMapper.updateEmbedding(workspaceId, documentVersionId,
					chunks.get(index).sequenceNumber(), vectorLiteral(embedding)) != 1) {
				throw new IllegalStateException("Failed to update chunk embedding " + index);
			}
		}
	}

	/** 将模型数组编码为 PostgreSQL pgvector 接受的文本字面量。 */
	private static String vectorLiteral(float[] values) {
		StringBuilder result = new StringBuilder(values.length * 10).append('[');
		for (int index = 0; index < values.length; index++) {
			if (index > 0) result.append(',');
			result.append(values[index]);
		}
		return result.append(']').toString();
	}
}
