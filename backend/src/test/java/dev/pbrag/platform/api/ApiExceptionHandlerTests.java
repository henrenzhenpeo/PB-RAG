package dev.pbrag.platform.api;

import static org.assertj.core.api.Assertions.assertThat;

import dev.pbrag.observability.RequestLoggingFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/** 全局异常处理器在异步客户端断开时不得尝试写入 JSON 响应。 */
class ApiExceptionHandlerTests {

	@Test
	void handlesDisconnectedSseClientWithoutCreatingAnErrorBody() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		ApiExceptionHandler handler = new ApiExceptionHandler();

		handler.handleDisconnectedClient(
				new AsyncRequestNotUsableException("response is no longer usable"), request);

		assertThat(request.getAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE))
				.isEqualTo("CLIENT_DISCONNECTED");
	}
}
