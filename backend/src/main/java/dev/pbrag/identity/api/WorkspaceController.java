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

@RestController
@RequestMapping("/api/workspaces")
public class WorkspaceController {

	private final WorkspaceService service;

	public WorkspaceController(WorkspaceService service) {
		this.service = service;
	}

	@PostMapping
	public ResponseEntity<ApiResponse<WorkspaceResponse>> create(
			@Valid @RequestBody CreateWorkspaceRequest request) {
		Workspace workspace = service.create(request.name());
		return ApiResponses.created(
				URI.create("/api/workspaces/" + workspace.id()),
				WorkspaceResponse.from(workspace));
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<WorkspaceResponse>>> list() {
		return ApiResponses.ok(service.list().stream().map(WorkspaceResponse::from).toList());
	}

	public record CreateWorkspaceRequest(
			@NotBlank @Size(max = 120) String name) {
	}

	public record WorkspaceResponse(UUID id, String name) {
		static WorkspaceResponse from(Workspace workspace) {
			return new WorkspaceResponse(workspace.id(), workspace.name());
		}
	}
}
