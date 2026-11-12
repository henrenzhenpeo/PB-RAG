package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.service.DocumentUploadResult;
import dev.pbrag.ingestion.service.DocumentUploadService;
import dev.pbrag.ingestion.service.IngestionCreationResult;
import dev.pbrag.ingestion.service.IngestionCreationService;
import dev.pbrag.ingestion.service.IngestionSignalService;
import dev.pbrag.ingestion.storage.ObjectStorage;
import dev.pbrag.ingestion.storage.StoredObject;
import dev.pbrag.platform.api.InvalidRequestException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文档上传编排实现：先校验并流式写入对象存储，再在数据库事务中创建资源。
 * 数据库创建失败或内容重复时补偿删除本次临时对象，避免产生无主文件。
 */
@Service
public class DocumentUploadServiceImpl implements DocumentUploadService {

	private static final Logger log = LoggerFactory.getLogger(DocumentUploadServiceImpl.class);
	private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
			"text/markdown", "text/plain", "application/pdf", "application/octet-stream");

	private final ObjectStorage storage;
	private final IngestionCreationService creationService;
	private final IngestionSignalService signalService;
	private final long maximumBytes;

	public DocumentUploadServiceImpl(
			ObjectStorage storage,
			IngestionCreationService creationService,
			IngestionSignalService signalService,
			@Value("${pbrag.ingestion.max-file-size:5242880}") long maximumBytes) {
		this.storage = storage;
		this.creationService = creationService;
		this.signalService = signalService;
		this.maximumBytes = maximumBytes;
	}

	/** 对象存储与数据库无法组成分布式事务，因此显式采用“先存储、失败补偿”策略。 */
	@Override
	public DocumentUploadResult upload(UUID workspaceId, UUID knowledgeBaseId, MultipartFile file) {
		return storeAndCreate(workspaceId, knowledgeBaseId, null, file);
	}

	/** 新版本复用上传校验、对象存储和补偿语义，仅改变事务创建目标。 */
	@Override
	public DocumentUploadResult uploadVersion(
			UUID workspaceId, UUID knowledgeBaseId, UUID documentId, MultipartFile file) {
		return storeAndCreate(workspaceId, knowledgeBaseId, documentId, file);
	}

	private DocumentUploadResult storeAndCreate(
			UUID workspaceId, UUID knowledgeBaseId, UUID documentId, MultipartFile file) {
		String filename = validate(file);
		StoredObject stored;
		try (InputStream input = file.getInputStream()) {
			stored = storage.store(input, filename, maximumBytes);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to store uploaded file", exception);
		}

		try {
			String mediaType = filename.toLowerCase(Locale.ROOT).endsWith(".pdf")
					? "application/pdf" : "text/markdown";
			IngestionCreationResult created = documentId == null
					? creationService.create(
							workspaceId, knowledgeBaseId, filename, mediaType, stored.sha256(), stored.key())
					: creationService.createVersion(
							workspaceId, knowledgeBaseId, documentId, mediaType,
							stored.sha256(), stored.key());
			if (created.job() == null) {
				throw new IllegalStateException("Existing document version has no ingestion job");
			}
			if (created.duplicate()) {
				deleteQuietly(stored.key());
			}
			DocumentUploadResult result = new DocumentUploadResult(
					created.documentId(), created.documentVersionId(), created.job(),
					stored.sha256(), created.duplicate());
			if (!created.duplicate()) signalService.notifyJobAvailable(created.job().id());
			return result;
		}
		catch (RuntimeException exception) {
			deleteQuietly(stored.key());
			throw exception;
		}
	}

	private String validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidRequestException("Document file must not be empty");
		}
		if (file.getSize() > maximumBytes) {
			throw new InvalidRequestException("Document file exceeds the 5 MB limit");
		}
		String filename = safeFilename(file.getOriginalFilename());
		String lowerName = filename.toLowerCase(Locale.ROOT);
		if (!lowerName.endsWith(".md") && !lowerName.endsWith(".pdf")) {
			throw new InvalidRequestException("Only .md and .pdf files are supported");
		}
		String mediaType = file.getContentType();
		if (mediaType != null && !ALLOWED_MEDIA_TYPES.contains(mediaType.toLowerCase(Locale.ROOT))) {
			throw new InvalidRequestException("Unsupported document media type");
		}
		return filename;
	}

	/** 只保留最终文件名，阻断客户端文件名携带目录造成的路径穿越。 */
	private static String safeFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new InvalidRequestException("Uploaded file must have a filename");
		}
		try {
			String portableName = originalFilename.replace('\\', '/');
			String filename = Path.of(portableName).getFileName().toString().strip();
			if (filename.isEmpty() || filename.length() > 255) {
				throw new InvalidRequestException("Document filename is invalid");
			}
			return filename;
		}
		catch (InvalidPathException exception) {
			throw new InvalidRequestException("Document filename is invalid");
		}
	}

	/** 补偿失败不能覆盖原始业务异常，但必须记录以便运维清理孤儿对象。 */
	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (IOException exception) {
			log.error("Failed to compensate stored object {}", key, exception);
		}
	}
}
