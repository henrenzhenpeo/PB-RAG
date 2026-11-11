package dev.pbrag.retrieval.rerank;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.util.List;

/** 隔离 Production RAG 与具体供应商协议的文本重排端口。 */
public interface RerankClient {

	/** 返回按相关性降序排列的候选下标和分数。 */
	List<RerankScore> rerank(String query, List<RetrievalHit> candidates, int topN);

	/** 当前运行是否配置了真实重排服务。 */
	boolean enabled();

	/** 记录到检索运行中的稳定模型标识。 */
	String model();

	/** 供应商返回的一条候选位置和相关性分数。 */
	record RerankScore(int index, double score) {
	}
}
