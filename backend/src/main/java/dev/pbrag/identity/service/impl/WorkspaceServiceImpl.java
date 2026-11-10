package dev.pbrag.identity.service.impl;

import dev.pbrag.identity.domain.Workspace;
import dev.pbrag.identity.mapper.WorkspaceMapper;
import dev.pbrag.identity.service.WorkspaceService;
import dev.pbrag.platform.api.ConflictException;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Workspace 业务实现。事务边界放在 ServiceImpl，Mapper 只负责数据库访问。 */
@Service
public class WorkspaceServiceImpl implements WorkspaceService {

	private final WorkspaceMapper workspaceMapper;

	/** 注入 Workspace 数据访问组件。 */
	public WorkspaceServiceImpl(WorkspaceMapper workspaceMapper) {
		this.workspaceMapper = workspaceMapper;
	}

	/** 校验并持久化 Workspace，名称冲突时转换为业务冲突异常。 */
	@Override
	@Transactional
	public Workspace create(String rawName) {
		Workspace workspace = new Workspace(UUID.randomUUID(), rawName.strip());
		try {
			workspaceMapper.insert(workspace);
			return workspace;
		}
		catch (DuplicateKeyException exception) {
			throw new ConflictException("Workspace name already exists", exception);
		}
	}

	/** 按稳定顺序读取全部 Workspace。 */
	@Override
	@Transactional(readOnly = true)
	public List<Workspace> list() {
		return workspaceMapper.selectAllOrdered();
	}

	/** 按 ID 查询 Workspace，并统一处理资源不存在语义。 */
	@Override
	@Transactional(readOnly = true)
	public Workspace require(UUID workspaceId) {
		Workspace workspace = workspaceMapper.selectById(workspaceId);
		if (workspace == null) {
			throw new ResourceNotFoundException("Workspace not found");
		}
		return workspace;
	}
}
