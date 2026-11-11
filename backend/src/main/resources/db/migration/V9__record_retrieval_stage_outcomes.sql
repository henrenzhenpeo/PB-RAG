ALTER TABLE retrieval_runs
    ADD COLUMN final_stage VARCHAR(32),
    ADD COLUMN degradation_code VARCHAR(80),
    ADD CONSTRAINT ck_retrieval_runs_final_stage
        CHECK (final_stage IS NULL OR final_stage IN ('FUSION', 'RERANK'));

CREATE TABLE retrieval_stage_summaries (
    retrieval_run_id UUID NOT NULL,
    workspace_id UUID NOT NULL,
    stage VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    candidate_count INTEGER NOT NULL CHECK (candidate_count >= 0),
    latency_ms BIGINT NOT NULL CHECK (latency_ms >= 0),
    error_code VARCHAR(80),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (retrieval_run_id, stage),
    CONSTRAINT ck_retrieval_stage_summaries_stage
        CHECK (stage IN ('VECTOR', 'KEYWORD', 'FUSION', 'RERANK', 'CONTEXT')),
    CONSTRAINT ck_retrieval_stage_summaries_status
        CHECK (status IN ('COMPLETED', 'SKIPPED', 'DEGRADED')),
    CONSTRAINT fk_retrieval_stage_summaries_run_scope
        FOREIGN KEY (workspace_id, retrieval_run_id)
        REFERENCES retrieval_runs (workspace_id, id)
        ON DELETE CASCADE
);
