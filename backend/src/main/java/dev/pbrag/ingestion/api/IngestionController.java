package dev.pbrag.ingestion.api;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.service.DocumentUploadResult;
import dev.pbrag.ingestion.service.DocumentUploadService;
import dev.pbrag.ingestion.service.IngestionJobService;
import dev.pbrag.platform.api.ApiResponse;
import dev.pbrag.platform.api.ApiResponses;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 文档上传和异步入库任务状态查询 API；上传只负责接收文件并快速返回任务。 */
@RestController
@RequestMapping("/api/workspaces/{workspaceId}")
public class IngestionController {

	private final DocumentUploadService uploadService;
	private final IngestionJobService jobService;

	/** 注入上传编排与任务查询服务。 */
	public IngestionController(DocumentUploadService uploadService, IngestionJobService jobService) {
		this.uploadService = uploadService;
		this.jobService = jobService;
	}

	/** 接收 Markdown/PDF 文件，保存原文件并快速返回异步入库任务。 */
	@PostMapping("/knowledge-bases/{knowledgeBaseId}/documents")
	public ResponseEntity<ApiResponse<UploadResponse>> upload(
			@PathVariable UUID workspaceId,
			@PathVariable UUID knowledgeBaseId,
			@RequestPart("file") MultipartFile file) {
		DocumentUploadResult result = uploadService.upload(workspaceId, knowledgeBaseId, file);
		UploadResponse response = UploadResponse.from(result);
		if (result.duplicate()) {
			return ApiResponses.ok(response);
		}
		return ApiResponses.created(
				URI.create("/api/workspaces/" + workspaceId + "/ingestion-jobs/" + result.job().id()),
				response);
	}

	/** 查询 Workspace 作用域内的单个入库任务最新状态。 */
	@GetMapping("/ingestion-jobs/{jobId}")
	public ResponseEntity<ApiResponse<JobResponse>> getJob(
			@PathVariable UUID workspaceId,
			@PathVariable UUID jobId) {
		return ApiResponses.ok(JobResponse.from(jobService.require(workspaceId, jobId)));
	}

	public record UploadResponse(
			UUID documentId, UUID documentVersionId, UUID jobId,
			String contentSha256, boolean duplicate, JobResponse job) {
		/** 将上传服务结果转换为 HTTP 响应。 */
		static UploadResponse from(DocumentUploadResult result) {
			return new UploadResponse(
					result.documentId(), result.documentVersionId(), result.job().id(),
					result.contentSha256(), result.duplicate(), JobResponse.from(result.job()));
		}
	}

	public record JobResponse(
			UUID id, UUID workspaceId, UUID documentVersionId,
			String status, String stage, int attemptCount, int maxAttempts,
			String errorCode, String errorMessage,
			OffsetDateTime availableAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
		/** 将任务领域对象转换为前端轮询使用的状态快照。 */
		static JobResponse from(IngestionJob job) {
			return new JobResponse(
					job.id(), job.workspaceId(), job.documentVersionId(),
					job.status().name(), job.stage().name(), job.attemptCount(), job.maxAttempts(),
					job.errorCode(), job.errorMessage(), job.availableAt(), job.createdAt(), job.updatedAt());
		}
	}
}
