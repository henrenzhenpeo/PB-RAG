package dev.pbrag.retrieval.rerank;

import dev.pbrag.retrieval.domain.RetrievalHit;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
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
			@Value("${pbrag.retrieval.rerank.model:qwen3.7-text-rerank}") String model,
			@Value("${pbrag.retrieval.rerank.connect-timeout:2s}") Duration connectTimeout,
			@Value("${pbrag.retrieval.rerank.read-timeout:10s}") Duration readTimeout) {
		if (url.isBlank() || apiKey.isBlank()) throw new IllegalArgumentException("Rerank URL and API key are required when rerank is enabled");
		if (connectTimeout.isZero() || connectTimeout.isNegative()
				|| readTimeout.isZero() || readTimeout.isNegative()) {
			throw new IllegalArgumentException("Rerank timeouts must be positive");
		}
		this.url = url;
		this.model = model;
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(connectTimeout);
		requestFactory.setReadTimeout(readTimeout);
		this.restClient = builder.clone().requestFactory(requestFactory)
				.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey).build();
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
		catch (HttpClientErrorException exception) {
			throw new RerankProtocolException(
					"Rerank provider rejected the request with status " + exception.getStatusCode().value(), exception);
		}
		if (response == null || response.output() == null || response.output().results() == null) {
			throw new RerankProtocolException("Rerank response is missing results");
		}
		int expectedMaximum = Math.min(topN, candidates.size());
		if (response.output().results().isEmpty() || response.output().results().size() > expectedMaximum) {
			throw new RerankProtocolException("Rerank response contains an invalid result count");
		}
		Set<Integer> indexes = new HashSet<>();
		return response.output().results().stream().map(result -> {
			if (result == null) throw new RerankProtocolException("Rerank response contains a null result");
			if (result.index() < 0 || result.index() >= candidates.size()) throw new RerankProtocolException("Rerank response contains an invalid candidate index");
			if (!indexes.add(result.index())) throw new RerankProtocolException("Rerank response contains a duplicate candidate index");
			if (!Double.isFinite(result.relevance_score())) throw new RerankProtocolException("Rerank response contains a non-finite score");
			return new RerankScore(result.index(), result.relevance_score());
		}).sorted(Comparator.comparingDouble(RerankScore::score).reversed()
				.thenComparingInt(RerankScore::index)).toList();
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
