package dev.pbrag.ingestion.service.impl;

import dev.pbrag.ingestion.domain.IngestionJob;
import dev.pbrag.ingestion.mapper.IngestionJobMapper;
import dev.pbrag.ingestion.service.IngestionCreationResult;
import dev.pbrag.ingestion.service.IngestionCreationService;
import dev.pbrag.knowledge.domain.Document;
import dev.pbrag.knowledge.domain.DocumentVersion;
import dev.pbrag.knowledge.mapper.DocumentMapper;
import dev.pbrag.knowledge.mapper.DocumentVersionMapper;
import dev.pbrag.knowledge.mapper.KnowledgeBaseMapper;
import dev.pbrag.platform.api.ResourceNotFoundException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionCreationServiceImpl implements IngestionCreationService {
	private static final Logger log = LoggerFactory.getLogger(IngestionCreationServiceImpl.class);

	private final KnowledgeBaseMapper knowledgeBaseMapper;
	private final DocumentMapper documentMapper;
	private final DocumentVersionMapper versionMapper;
	private final IngestionJobMapper jobMapper;

	public IngestionCreationServiceImpl(
			KnowledgeBaseMapper knowledgeBaseMapper,
			DocumentMapper documentMapper,
			DocumentVersionMapper versionMapper,
			IngestionJobMapper jobMapper) {
		this.knowledgeBaseMapper = knowledgeBaseMapper;
		this.documentMapper = documentMapper;
		this.versionMapper = versionMapper;
		this.jobMapper = jobMapper;
	}

	@Override
	@Transactional
	public IngestionCreationResult create(
			UUID workspaceId, UUID knowledgeBaseId, String displayName,
			String contentSha256, String storageKey) {
		if (knowledgeBaseMapper.lockByWorkspaceIdAndId(workspaceId, knowledgeBaseId) == null) {
			throw new ResourceNotFoundException("Knowledge base not found");
		}

		DocumentVersion existing = versionMapper.selectByKnowledgeBaseAndHash(
				workspaceId, knowledgeBaseId, contentSha256);
		if (existing != null) {
			IngestionJob job = jobMapper.selectByDocumentVersionId(existing.id());
			log.info(
					"ingestion_upload_deduplicated workspaceId={} knowledgeBaseId={} documentId={} documentVersionId={} jobId={} contentSha256={}",
					workspaceId, knowledgeBaseId, existing.documentId(), existing.id(),
					job == null ? null : job.id(), shortHash(contentSha256));
			return new IngestionCreationResult(existing.documentId(), existing.id(), job, true);
		}

		UUID documentId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		Document document = new Document(documentId, workspaceId, knowledgeBaseId, displayName, "text/markdown");
		DocumentVersion version = new DocumentVersion(
				versionId, workspaceId, documentId, 1, contentSha256, storageKey);
		IngestionJob job = new IngestionJob(UUID.randomUUID(), workspaceId, versionId);

		documentMapper.insert(document);
		versionMapper.insert(version);
		if (documentMapper.updateCurrentVersion(workspaceId, documentId, versionId) != 1) {
			throw new IllegalStateException("Failed to select the current document version");
		}
		jobMapper.insert(job);
		IngestionJob persistedJob = jobMapper.selectByWorkspaceIdAndId(workspaceId, job.id());
		if (persistedJob == null) {
			throw new IllegalStateException("Failed to reload the created ingestion job");
		}
		log.info(
				"ingestion_job_created workspaceId={} knowledgeBaseId={} documentId={} documentVersionId={} jobId={} contentSha256={}",
				workspaceId, knowledgeBaseId, documentId, versionId, persistedJob.id(), shortHash(contentSha256));
		return new IngestionCreationResult(documentId, versionId, persistedJob, false);
	}

	private static String shortHash(String sha256) {
		return sha256 == null || sha256.length() <= 12 ? sha256 : sha256.substring(0, 12);
	}
}
