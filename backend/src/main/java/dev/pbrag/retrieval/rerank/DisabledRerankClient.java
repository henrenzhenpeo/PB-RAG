package dev.pbrag.retrieval.rerank;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 未配置远程模型时显式关闭重排，由编排层保留 RRF 顺序。 */
@Component
@ConditionalOnProperty(name = "pbrag.retrieval.rerank.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledRerankClient implements RerankClient {

	@Override
	public List<RerankScore> rerank(String query, List<RetrievalHit> candidates, int topN) {
		throw new IllegalStateException("Rerank is disabled");
	}

	@Override
	public boolean enabled() {
		return false;
	}

	@Override
	public String model() {
		return "disabled";
	}
}
