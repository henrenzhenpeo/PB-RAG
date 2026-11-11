CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_chunks_content_trgm
    ON chunks USING gin (content gin_trgm_ops);

CREATE TABLE retrieval_runs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    knowledge_base_id UUID NOT NULL,
    question TEXT NOT NULL,
    strategy VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    embedding_model VARCHAR(160),
    rerank_model VARCHAR(160),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    latency_ms BIGINT,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_retrieval_runs_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT ck_retrieval_runs_status CHECK (status IN ('RUNNING', 'COMPLETED', 'FAILED')),
    CONSTRAINT fk_retrieval_runs_knowledge_base_scope
        FOREIGN KEY (workspace_id, knowledge_base_id)
        REFERENCES knowledge_bases (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_runs_scope_created
    ON retrieval_runs (workspace_id, knowledge_base_id, created_at DESC);

CREATE TABLE retrieval_candidates (
    id UUID PRIMARY KEY,
    retrieval_run_id UUID NOT NULL REFERENCES retrieval_runs (id) ON DELETE CASCADE,
    workspace_id UUID NOT NULL,
    stage VARCHAR(24) NOT NULL,
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    rank INTEGER NOT NULL CHECK (rank > 0),
    score DOUBLE PRECISION NOT NULL,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_retrieval_candidates_stage
        CHECK (stage IN ('VECTOR', 'KEYWORD', 'FUSION', 'RERANK', 'CONTEXT')),
    CONSTRAINT uq_retrieval_candidates_run_stage_rank UNIQUE (retrieval_run_id, stage, rank),
    CONSTRAINT uq_retrieval_candidates_run_stage_chunk UNIQUE (retrieval_run_id, stage, chunk_id),
    CONSTRAINT fk_retrieval_candidates_run_scope
        FOREIGN KEY (workspace_id, retrieval_run_id)
        REFERENCES retrieval_runs (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_retrieval_candidates_run_stage
    ON retrieval_candidates (retrieval_run_id, stage, rank);
