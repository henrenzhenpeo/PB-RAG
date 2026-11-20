package dev.pbrag.tool;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 显式工具白名单；重复名称在启动时失败。 */
@Component
public class ToolRegistry {
	private final Map<String, AgentTool> tools;

	/** 从受 Spring 管理的工具构建不可变注册表。 */
	public ToolRegistry(List<AgentTool> tools) {
		this.tools = tools.stream().collect(Collectors.toUnmodifiableMap(AgentTool::name, Function.identity()));
	}

	/** 仅返回本运行已授权且版本匹配的工具。 */
	public AgentTool require(String name, Map<String, String> allowedVersions) {
		AgentTool tool = tools.get(name);
		if (tool == null || !tool.version().equals(allowedVersions.get(name))) {
			throw new IllegalArgumentException("Tool is unavailable for this run: " + name);
		}
		return tool;
	}

	/** 创建运行快照时读取当前可用版本。 */
	public Map<String, String> versions() {
		return tools.values().stream().collect(Collectors.toUnmodifiableMap(AgentTool::name, AgentTool::version));
	}
}
