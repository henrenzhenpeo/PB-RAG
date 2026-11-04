package dev.pbrag.identity.service;

import dev.pbrag.identity.domain.Workspace;
import java.util.List;
import java.util.UUID;

/** Workspace 应用服务，对控制器暴露稳定的业务用例。 */
public interface WorkspaceService {

	Workspace create(String rawName);

	List<Workspace> list();

	Workspace require(UUID workspaceId);
}
