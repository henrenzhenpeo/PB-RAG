package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.service.DocumentUploadResult;
import dev.pbrag.ingestion.service.DocumentUploadService;
import dev.pbrag.ingestion.service.IngestionCreationResult;
import dev.pbrag.ingestion.service.IngestionCreationService;
import dev.pbrag.ingestion.storage.ObjectStorage;
import dev.pbrag.ingestion.storage.StoredObject;
import dev.pbrag.platform.api.InvalidRequestException;
import java.io.IOException;
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

@Service
public class DocumentUploadServiceImpl implements DocumentUploadService {

	private static final Logger log = LoggerFactory.getLogger(DocumentUploadServiceImpl.class);
	private static final Set<String> ALLOWED_MEDIA_TYPES = Set.of(
			"text/markdown", "text/plain", "application/octet-stream");

	private final ObjectStorage storage;
	private final IngestionCreationService creationService;
	private final long maximumBytes;

	public DocumentUploadServiceImpl(
			ObjectStorage storage,
			IngestionCreationService creationService,
			@Value("${pbrag.ingestion.max-file-size:5242880}") long maximumBytes) {
		this.storage = storage;
		this.creationService = creationService;
		this.maximumBytes = maximumBytes;
	}

	@Override
	public DocumentUploadResult upload(UUID workspaceId, UUID knowledgeBaseId, MultipartFile file) {
		String filename = validate(file);
		StoredObject stored;
		try {
			stored = storage.store(file.getInputStream(), filename, maximumBytes);
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to store uploaded file", exception);
		}

		try {
			IngestionCreationResult created = creationService.create(
					workspaceId, knowledgeBaseId, filename, stored.sha256(), stored.key());
			if (created.job() == null) {
				throw new IllegalStateException("Existing document version has no ingestion job");
			}
			if (created.duplicate()) {
				deleteQuietly(stored.key());
			}
			return new DocumentUploadResult(
					created.documentId(), created.documentVersionId(), created.job(),
					stored.sha256(), created.duplicate());
		}
		catch (RuntimeException exception) {
			deleteQuietly(stored.key());
			throw exception;
		}
	}

	private String validate(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new InvalidRequestException("Markdown file must not be empty");
		}
		if (file.getSize() > maximumBytes) {
			throw new InvalidRequestException("Markdown file exceeds the 5 MB limit");
		}
		String filename = safeFilename(file.getOriginalFilename());
		if (!filename.toLowerCase(Locale.ROOT).endsWith(".md")) {
			throw new InvalidRequestException("Only .md files are supported");
		}
		String mediaType = file.getContentType();
		if (mediaType != null && !ALLOWED_MEDIA_TYPES.contains(mediaType.toLowerCase(Locale.ROOT))) {
			throw new InvalidRequestException("Unsupported Markdown media type");
		}
		return filename;
	}

	private static String safeFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			throw new InvalidRequestException("Uploaded file must have a filename");
		}
		try {
			String portableName = originalFilename.replace('\\', '/');
			String filename = Path.of(portableName).getFileName().toString().strip();
			if (filename.isEmpty() || filename.length() > 255) {
				throw new InvalidRequestException("Markdown filename is invalid");
			}
			return filename;
		}
		catch (InvalidPathException exception) {
			throw new InvalidRequestException("Markdown filename is invalid");
		}
	}

	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (IOException exception) {
			log.error("Failed to compensate stored object {}", key, exception);
		}
	}
}
