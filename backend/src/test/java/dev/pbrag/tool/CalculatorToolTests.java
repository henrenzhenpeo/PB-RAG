package dev.pbrag.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** 验证模型参数必须通过精确 Schema 及边界检查。 */
class CalculatorToolTests {
	private final CalculatorTool tool = new CalculatorTool();
	private final ObjectMapper json = new ObjectMapper();

	@Test
	void calculatesAfterValidation() throws tools.jackson.core.JacksonException {
		var arguments = tool.validate(json.readTree("{" +
				"\"operation\":\"divide\",\"left\":12,\"right\":3}"));
		assertEquals("4", tool.execute(UUID.randomUUID(), UUID.randomUUID(), arguments));
	}

	@Test
	void rejectsExtraPropertiesAndDivisionByZero() throws tools.jackson.core.JacksonException {
		assertThrows(IllegalArgumentException.class, () -> tool.validate(json.readTree("{" +
				"\"operation\":\"add\",\"left\":1,\"right\":2,\"workspaceId\":\"other\"}")));
		var zero = tool.validate(json.readTree("{" +
				"\"operation\":\"divide\",\"left\":1,\"right\":0}"));
		assertThrows(ArithmeticException.class,
				() -> tool.execute(UUID.randomUUID(), UUID.randomUUID(), zero));
	}
}
