package dev.pbrag.ingestion.mapper;

import dev.pbrag.ingestion.chunking.ChunkDraft;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Chunk 批次替换与 pgvector 写入 Mapper，所有操作同时约束 Workspace 和文档版本。 */
@Mapper
public interface ChunkMapper {

	/** 删除指定文档版本已有的全部 Chunk。 */
	int deleteByDocumentVersion(
			@Param("workspaceId") UUID workspaceId,
			@Param("documentVersionId") UUID documentVersionId);

	/** 插入一个包含稳定来源位置的 Chunk。 */
	int insert(
			@Param("id") UUID id,
			@Param("workspaceId") UUID workspaceId,
			@Param("documentVersionId") UUID documentVersionId,
			@Param("chunk") ChunkDraft chunk,
			@Param("chunkingVersion") String chunkingVersion);

	/** 按序号写入指定 Chunk 的 pgvector 向量。 */
	int updateEmbedding(
			@Param("workspaceId") UUID workspaceId,
			@Param("documentVersionId") UUID documentVersionId,
			@Param("sequenceNumber") int sequenceNumber,
			@Param("embedding") String embedding);
}
