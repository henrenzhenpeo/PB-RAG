package dev.pbrag.agent.service;

import java.util.UUID;

/** 生产 Agent 的中立运行入口。 */
public interface AgentOrchestrator {
	/** 创建并执行首版同步 Run，返回可查询的运行 ID。 */
	UUID start(UUID workspaceId, UUID knowledgeBaseId, String goal);
}
