package dev.pbrag.ingestion.service;

import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

/** 校验并保存上传文件，然后建立异步入库任务的应用服务。 */
public interface DocumentUploadService {

	/** 校验并存储上传内容，然后创建或复用对应的异步入库任务。 */
	DocumentUploadResult upload(UUID workspaceId, UUID knowledgeBaseId, MultipartFile file);
}
