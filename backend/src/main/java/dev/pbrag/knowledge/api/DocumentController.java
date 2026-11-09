package dev.pbrag.knowledge.api;

import dev.pbrag.knowledge.domain.DocumentListItem;
import dev.pbrag.knowledge.service.DocumentQueryService;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/knowledge-bases/{knowledgeBaseId}/documents")
public class DocumentController {

	private final DocumentQueryService service;

	public DocumentController(DocumentQueryService service) {
		this.service = service;
	}

	@GetMapping
	public ResponseEntity<ApiResponse<List<DocumentResponse>>> list(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId) {
		return ApiResponses.ok(service.list(workspaceId, knowledgeBaseId).stream()
				.map(DocumentResponse::from).toList());
	}

	public record DocumentResponse(
			UUID id, UUID workspaceId, UUID knowledgeBaseId, String displayName, String mediaType,
			VersionResponse currentVersion, JobResponse ingestionJob,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		static DocumentResponse from(DocumentListItem item) {
			return new DocumentResponse(
					item.getDocumentId(), item.getWorkspaceId(), item.getKnowledgeBaseId(),
					item.getDisplayName(), item.getMediaType(),
					new VersionResponse(item.getVersionId(), item.getVersionNumber(),
							item.getContentSha256(), item.getVersionCreatedAt()),
					JobResponse.from(item), item.getDocumentCreatedAt(), item.getDocumentUpdatedAt());
		}
	}

	public record VersionResponse(
			UUID id, int versionNumber, String contentSha256, OffsetDateTime createdAt) {
	}

	public record JobResponse(
			UUID id, String status, String stage, int attemptCount, int maxAttempts,
			String errorCode, String errorMessage, OffsetDateTime availableAt,
			OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		static JobResponse from(DocumentListItem item) {
			if (item.getJobId() == null) return null;
			return new JobResponse(
					item.getJobId(), item.getJobStatus().name(), item.getJobStage().name(),
					item.getAttemptCount(), item.getMaxAttempts(), item.getErrorCode(),
					item.getErrorMessage(), item.getAvailableAt(), item.getJobCreatedAt(),
					item.getJobUpdatedAt());
		}
	}
}
