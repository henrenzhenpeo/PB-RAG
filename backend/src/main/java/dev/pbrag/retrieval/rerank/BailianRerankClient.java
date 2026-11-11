package dev.pbrag.retrieval.rerank;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/** 阿里云百炼文本重排 HTTP 适配器，供应商协议止于本类。 */
@Component
@ConditionalOnProperty(name = "pbrag.retrieval.rerank.enabled", havingValue = "true")
public class BailianRerankClient implements RerankClient {
	private static final String INSTRUCT = "Given a web search query, retrieve relevant passages that answer the query.";
	private final RestClient restClient;
	private final String url;
	private final String model;

	public BailianRerankClient(RestClient.Builder builder,
			@Value("${pbrag.retrieval.rerank.url}") String url,
			@Value("${pbrag.retrieval.rerank.api-key}") String apiKey,
			@Value("${pbrag.retrieval.rerank.model:qwen3.7-text-rerank}") String model) {
		if (url.isBlank() || apiKey.isBlank()) throw new IllegalArgumentException("Rerank URL and API key are required when rerank is enabled");
		this.url = url;
		this.model = model;
		this.restClient = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).build();
	}

	@Override
	public List<RerankScore> rerank(String query, List<RetrievalHit> candidates, int topN) {
		if (candidates.isEmpty()) return List.of();
		Request request = new Request(model,
				new Input(query, candidates.stream().map(RetrievalHit::content).toList()),
				new Parameters(Math.min(topN, candidates.size()), false, INSTRUCT));
		Response response;
		try {
			response = restClient.post().uri(url).contentType(MediaType.APPLICATION_JSON)
					.body(request).retrieve().body(Response.class);
		}
		catch (HttpClientErrorException.TooManyRequests | HttpServerErrorException | ResourceAccessException exception) {
			throw new RerankTransientException("Rerank provider is temporarily unavailable", exception);
		}
		if (response == null || response.output() == null || response.output().results() == null) {
			throw new IllegalStateException("Rerank response is missing results");
		}
		Set<Integer> indexes = new HashSet<>();
		return response.output().results().stream().map(result -> {
			if (result.index() < 0 || result.index() >= candidates.size()) throw new IllegalStateException("Rerank response contains an invalid candidate index");
			if (!indexes.add(result.index())) throw new IllegalStateException("Rerank response contains a duplicate candidate index");
			return new RerankScore(result.index(), result.relevance_score());
		}).toList();
	}

	@Override public boolean enabled() { return true; }
	@Override public String model() { return model; }

	private record Request(String model, Input input, Parameters parameters) { }
	private record Input(String query, List<String> documents) { }
	private record Parameters(int top_n, boolean return_documents, String instruct) { }
	private record Response(Output output, String request_id, String code, String message) { }
	private record Output(List<Result> results) { }
	private record Result(int index, double relevance_score) { }
}
