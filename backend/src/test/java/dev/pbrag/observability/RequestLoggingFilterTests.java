package dev.pbrag.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTests {

	private final RequestLoggingFilter filter = new RequestLoggingFilter();

	@Test
	void preservesSafeRequestIdAndReturnsItToCaller() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest(
				"GET", "/api/workspaces/123e4567-e89b-12d3-a456-426614174000/knowledge-bases");
		request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "client-request-42");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
			assertThat(MDC.get("requestId")).isEqualTo("client-request-42");
			assertThat(MDC.get("workspaceId")).isEqualTo("123e4567-e89b-12d3-a456-426614174000");
			((MockHttpServletResponse) servletResponse).setStatus(201);
		});

		assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isEqualTo("client-request-42");
		assertThat(MDC.get("requestId")).isNull();
		assertThat(MDC.get("workspaceId")).isNull();
	}

	@Test
	void replacesUnsafeRequestId() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/workspaces");
		request.addHeader(RequestLoggingFilter.REQUEST_ID_HEADER, "unsafe value\nforged");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

		String generated = response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER);
		assertThat(UUID.fromString(generated)).isNotNull();
	}

	@Test
	void ignoresNonApiRequests() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> { });

		assertThat(response.getHeader(RequestLoggingFilter.REQUEST_ID_HEADER)).isNull();
	}
}
