package dev.pbrag.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pbrag.ingestion.parser.UnsupportedDocumentTypeException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

class IngestionFailureClassificationTests {
	@Test
	void classifiesPermanentInputAndConfigurationErrorsAsNonRetryable() {
		assertThat(IngestionWorker.classify(new UnsupportedDocumentTypeException("unsupported")))
				.isEqualTo(new IngestionWorker.IngestionFailure("UNSUPPORTED_DOCUMENT_TYPE", false));
		assertThat(IngestionWorker.classify(new IllegalArgumentException("PDF contains no extractable text")))
				.isEqualTo(new IngestionWorker.IngestionFailure("DOCUMENT_PARSE_FAILED", false));
		assertThat(IngestionWorker.classify(new EmbeddingDimensionMismatchException(1024, 1536)))
				.isEqualTo(new IngestionWorker.IngestionFailure("EMBEDDING_DIMENSION_MISMATCH", false));
	}

	@Test
	void classifiesProviderThrottlingAndTimeoutAsRetryable() {
		assertThat(IngestionWorker.classify(
				new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS)))
				.isEqualTo(new IngestionWorker.IngestionFailure("EMBEDDING_PROVIDER_UNAVAILABLE", true));
		assertThat(IngestionWorker.classify(new RuntimeException(
				new ResourceAccessException("timeout", new SocketTimeoutException()))))
				.isEqualTo(new IngestionWorker.IngestionFailure("EMBEDDING_PROVIDER_UNAVAILABLE", true));
	}

	@Test
	void classifiesOtherProviderClientErrorsAsNonRetryable() {
		assertThat(IngestionWorker.classify(new HttpClientErrorException(HttpStatus.BAD_REQUEST)))
				.isEqualTo(new IngestionWorker.IngestionFailure("EMBEDDING_REQUEST_REJECTED", false));
	}
}
