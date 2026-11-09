package dev.pbrag.ingestion.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentUploadService {

	DocumentUploadResult upload(UUID workspaceId, UUID knowledgeBaseId, MultipartFile file);
}
