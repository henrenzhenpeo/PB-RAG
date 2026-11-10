package dev.pbrag.identity.api;

import dev.pbrag.identity.domain.Workspace;
import dev.pbrag.identity.service.WorkspaceService;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workspace 创建与查询 API；Workspace 当前是共享知识分组，不是授权租户。 */
@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService service;

	/** 注入 Workspace 应用服务。 */
	public WorkspaceController(WorkspaceService service) {
		this.service = service;
	}

	/** 创建逻辑知识分组并返回新资源地址。 */
	@PostMapping
	public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
			@Valid @RequestBody CreateWorkspaceRequest request) {
		Workspace workspace = service.create(request.name());
		return ApiResponses.created(
				URI.create("/api/workspaces/" + workspace.id()),
				WorkspaceResponse.from(workspace));
	}

	/** 查询当前系统中的共享 Workspace 列表。 */
	@GetMapping
	public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> list() {
		return ApiResponses.ok(service.list().stream().map(WorkspaceResponse::from).toList());
	}

	public record CreateWorkspaceRequest(
			@NotBlank @Size(max = 120) String name) {
	}

	public record WorkspaceResponse(UUID id, String name) {
		/** 将 Workspace 领域对象转换为 API 响应。 */
		static WorkspaceResponse from(Workspace workspace) {
			return new WorkspaceResponse(workspace.id(), workspace.name());
		}
	}
}
