CREATE TABLE workspaces (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workspaces_name UNIQUE (name)
);

CREATE TABLE knowledge_bases (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_knowledge_bases_workspace_name UNIQUE (workspace_id, name),
    CONSTRAINT uq_knowledge_bases_workspace_id_id UNIQUE (workspace_id, id)
);

CREATE TABLE documents (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    knowledge_base_id UUID NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(120) NOT NULL,
    current_version_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_documents_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT fk_documents_knowledge_base_scope
        FOREIGN KEY (workspace_id, knowledge_base_id)
        REFERENCES knowledge_bases (workspace_id, id)
        ON DELETE CASCADE
);

CREATE TABLE document_versions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    document_id UUID NOT NULL,
    version_number INTEGER NOT NULL CHECK (version_number > 0),
    content_sha256 CHAR(64) NOT NULL,
    storage_key VARCHAR(512) NOT NULL,
    parser_version VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_document_versions_document_number UNIQUE (document_id, version_number),
    CONSTRAINT uq_document_versions_document_hash UNIQUE (document_id, content_sha256),
    CONSTRAINT uq_document_versions_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT fk_document_versions_document_scope
        FOREIGN KEY (workspace_id, document_id)
        REFERENCES documents (workspace_id, id)
        ON DELETE CASCADE
);

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version_scope
    FOREIGN KEY (workspace_id, current_version_id)
    REFERENCES document_versions (workspace_id, id);

CREATE TABLE chunks (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 0),
    content TEXT NOT NULL,
    start_offset INTEGER CHECK (start_offset IS NULL OR start_offset >= 0),
    end_offset INTEGER CHECK (end_offset IS NULL OR end_offset >= start_offset),
    source_position JSONB NOT NULL DEFAULT '{}'::jsonb,
    embedding vector(1536),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chunks_version_sequence UNIQUE (document_version_id, sequence_number),
    CONSTRAINT uq_chunks_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT fk_chunks_document_version_scope
        FOREIGN KEY (workspace_id, document_version_id)
        REFERENCES document_versions (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chunks_workspace_version
    ON chunks (workspace_id, document_version_id, sequence_number);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL DEFAULT 3 CHECK (max_attempts > 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMPTZ,
    error_code VARCHAR(80),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ingestion_jobs_version UNIQUE (document_version_id),
    CONSTRAINT uq_ingestion_jobs_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT ck_ingestion_jobs_status CHECK (
        status IN ('PENDING', 'RUNNING', 'RETRY_WAIT', 'COMPLETED', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_ingestion_jobs_stage CHECK (
        stage IN ('PENDING', 'PARSING', 'CHUNKING', 'EMBEDDING', 'INDEXING', 'COMPLETED')
    ),
    CONSTRAINT fk_ingestion_jobs_document_version_scope
        FOREIGN KEY (workspace_id, document_version_id)
        REFERENCES document_versions (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_ingestion_jobs_claim
    ON ingestion_jobs (status, available_at, created_at)
    WHERE status IN ('PENDING', 'RETRY_WAIT');
