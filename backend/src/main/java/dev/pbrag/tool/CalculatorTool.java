package dev.pbrag.tool;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** 确定性四则运算工具；参数结构和数值范围由代码校验。 */
@Component
public class CalculatorTool implements AgentTool {
	private static final String SCHEMA = """
			{"type":"object","additionalProperties":false,"required":["operation","left","right"],
			"properties":{"operation":{"type":"string","enum":["add","subtract","multiply","divide"]},
			"left":{"type":"number"},"right":{"type":"number"}}}
			""";

	/** 工具标识。 */
	@Override public String name() { return "calculator"; }
	/** Schema 或行为变化时递增。 */
	@Override public String version() { return "1.0.0"; }
	/** 模型可见的用途说明。 */
	@Override public String description() { return "对两个有限数字执行 add、subtract、multiply 或 divide 四则运算"; }
	/** 当前工具的精确输入契约。 */
	@Override public String inputSchema() { return SCHEMA; }

	/** 拒绝额外字段、错误类型、非有限值和过大的数。 */
	@Override
	public JsonNode validate(JsonNode arguments) {
		if (arguments == null || !arguments.isObject() || arguments.size() != 3 ||
				!arguments.has("operation") || !arguments.has("left") || !arguments.has("right") ||
				!arguments.get("operation").isString() || !arguments.get("left").isNumber() ||
				!arguments.get("right").isNumber()) {
			throw new IllegalArgumentException("Invalid calculator arguments");
		}
		String operation = arguments.get("operation").stringValue();
		if (!java.util.Set.of("add", "subtract", "multiply", "divide").contains(operation)) {
			throw new IllegalArgumentException("Unsupported calculator operation");
		}
		for (String key : new String[] { "left", "right" }) {
			BigDecimal value = arguments.get(key).decimalValue();
			if (value.precision() > 30 || value.scale() > 20 || value.abs().compareTo(new BigDecimal("1E30")) > 0) {
				throw new IllegalArgumentException("Calculator number exceeds limits");
			}
		}
		return arguments;
	}

	/** 执行只读运算；除零作为参数错误返回。 */
	@Override
	public String execute(UUID workspaceId, UUID knowledgeBaseId, JsonNode arguments) {
		validate(arguments);
		BigDecimal left = arguments.get("left").decimalValue();
		BigDecimal right = arguments.get("right").decimalValue();
		BigDecimal result = switch (arguments.get("operation").stringValue()) {
			case "add" -> left.add(right);
			case "subtract" -> left.subtract(right);
			case "multiply" -> left.multiply(right);
			case "divide" -> left.divide(right, MathContext.DECIMAL64);
			default -> throw new IllegalArgumentException("Unsupported calculator operation");
		};
		return result.stripTrailingZeros().toPlainString();
	}

	/** 数值运算可回放，审计仅存操作类型与参数是否通过校验。 */
	@Override
	public Map<String, Object> redactedArguments(JsonNode arguments) {
		return Map.of("operation", arguments.get("operation").stringValue(), "validated", true);
	}
}
