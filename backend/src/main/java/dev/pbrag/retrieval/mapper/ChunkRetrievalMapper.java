package dev.pbrag.retrieval.mapper;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 使用 PB-RAG 领域表执行带资源作用域的向量和 BM25 召回。 */
@Mapper
public interface ChunkRetrievalMapper {

	/**
	 * 在指定 Workspace 和知识库的当前文档版本中查询最相似的 Chunk。
	 * 资源条件直接进入 SQL，不能依赖模型或检索后的内存过滤。
	 */
	List<RetrievalHit> searchCurrentVersions(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("queryEmbedding") String queryEmbedding,
			@Param("minimumSimilarity") double minimumSimilarity,
			@Param("topK") int topK);

	/** 使用 ParadeDB BM25 和中文 Lindera 分词在同一资源及当前版本范围内召回候选。 */
	List<RetrievalHit> searchBm25CurrentVersions(
			@Param("workspaceId") UUID workspaceId,
			@Param("knowledgeBaseId") UUID knowledgeBaseId,
			@Param("query") String query,
			@Param("topK") int topK);
}
