package dev.pbrag.identity.service;

import dev.pbrag.identity.domain.Workspace;
import java.util.List;
import java.util.UUID;

/** Workspace 应用服务，对控制器暴露稳定的业务用例。 */
public interface WorkspaceService {

	/** 校验名称并创建 Workspace。 */
	Workspace create(String rawName);
	/** 按稳定顺序列出全部 Workspace。 */
	List<Workspace> list();
	/** 按 ID 获取 Workspace，不存在时抛出资源不存在异常。 */
	Workspace require(UUID workspaceId);
}
