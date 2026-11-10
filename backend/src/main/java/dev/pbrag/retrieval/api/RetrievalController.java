package dev.pbrag.retrieval.api;

import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import dev.pbrag.retrieval.domain.RetrievalHit;
import dev.pbrag.retrieval.service.RetrievalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供可独立观察的基础向量检索 API，便于调试和后续评测。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/retrieval")
@ConditionalOnProperty(name = "spring.ai.model.embedding", havingValue = "openai")
public class RetrievalController {

	private final RetrievalService service;

	/** 注入带 Workspace 作用域的检索服务。 */
	public RetrievalController(RetrievalService service) {
		this.service = service;
	}

	/** 将问题向量化并返回当前文档版本中的 Top-K Chunk 和来源位置。 */
	@PostMapping("/search")
	public ResponseEntity<ApiResponse<List<RetrievalHit>>> search(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId,
			@Valid @RequestBody SearchRequest input) {
		return ApiResponses.ok(service.search(workspaceId, knowledgeBaseId, input.question()));
	}

	/** 基础检索请求。 */
	public record SearchRequest(
			@NotBlank(message = "问题不能为空")
			@Size(max = 2000, message = "问题不能超过 2000 个字符")
			String question) {
	}
}
