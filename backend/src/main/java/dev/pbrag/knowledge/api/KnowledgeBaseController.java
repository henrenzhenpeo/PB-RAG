package dev.pbrag.knowledge.api;

import dev.pbrag.knowledge.domain.KnowledgeBase;
import dev.pbrag.knowledge.service.KnowledgeBaseService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Workspace 作用域内的知识库创建、列表与详情 API。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases")
public class KnowledgeBaseController {

	private final KnowledgeBaseService service;

	/** 注入知识库应用服务。 */
	public KnowledgeBaseController(KnowledgeBaseService service) {
		this.service = service;
	}

	/** 在指定 Workspace 下创建知识库并返回资源地址。 */
	@PostMapping
	public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> create(
			@PathVariable UUID workspaceId,
			@Valid @RequestBody CreateKnowledgeBaseRequest request) {
		KnowledgeBase knowledgeBase = service.create(workspaceId, request.name(), request.description());
		return ApiResponses.created(
				URI.create("/api/workspaces/" + workspaceId + "/knowledge-bases/" + knowledgeBase.id()),
				KnowledgeBaseResponse.from(knowledgeBase));
	}

	/** 查询指定 Workspace 下的全部知识库。 */
	@GetMapping
	public ResponseEntity<ApiResponse<List<KnowledgeBaseResponse>>> list(@PathVariable UUID workspaceId) {
		return ApiResponses.ok(
				service.list(workspaceId).stream().map(KnowledgeBaseResponse::from).toList());
	}

	/** 查询指定 Workspace 中的单个知识库。 */
	@GetMapping("/{knowledgeBaseId}")
	public ResponseEntity<ApiResponse<KnowledgeBaseResponse>> get(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId) {
		return ApiResponses.ok(
				KnowledgeBaseResponse.from(service.require(workspaceId, knowledgeBaseId)));
	}

	public record CreateKnowledgeBaseRequest(
			@NotBlank @Size(max = 160) String name,
			@Size(max = 2000) String description) {
	}

	public record KnowledgeBaseResponse(UUID id, UUID workspaceId, String name, String description) {
		/** 将知识库领域对象转换为稳定的 API 响应。 */
		static KnowledgeBaseResponse from(KnowledgeBase knowledgeBase) {
			return new KnowledgeBaseResponse(
					knowledgeBase.id(),
					knowledgeBase.workspaceId(),
					knowledgeBase.name(),
					knowledgeBase.description());
		}
	}
}
