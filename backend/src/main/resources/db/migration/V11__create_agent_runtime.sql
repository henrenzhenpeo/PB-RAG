CREATE TABLE agent_runs (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    knowledge_base_id UUID NOT NULL,
    goal TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    agent_key VARCHAR(120) NOT NULL,
    agent_version VARCHAR(40) NOT NULL,
    prompt_id VARCHAR(120) NOT NULL,
    prompt_version VARCHAR(40) NOT NULL,
    model_provider VARCHAR(120) NOT NULL,
    model_name VARCHAR(160) NOT NULL,
    configuration JSONB NOT NULL,
    next_step_sequence INTEGER NOT NULL DEFAULT 1 CHECK (next_step_sequence > 0),
    deadline_at TIMESTAMPTZ NOT NULL,
    cancel_requested_at TIMESTAMPTZ,
    lease_owner VARCHAR(160),
    lease_expires_at TIMESTAMPTZ,
    final_answer TEXT,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_agent_runs_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT ck_agent_runs_status CHECK (status IN
        ('PENDING', 'RUNNING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    CONSTRAINT ck_agent_runs_lease_pair CHECK ((lease_owner IS NULL) = (lease_expires_at IS NULL)),
    CONSTRAINT fk_agent_runs_knowledge_base_scope FOREIGN KEY (workspace_id, knowledge_base_id)
        REFERENCES knowledge_bases (workspace_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_runs_claim ON agent_runs (status, lease_expires_at, created_at)
    WHERE status IN ('PENDING', 'RUNNING');
CREATE INDEX idx_agent_runs_scope_created ON agent_runs (workspace_id, knowledge_base_id, created_at DESC);

CREATE TABLE agent_steps (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    run_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    attempt INTEGER NOT NULL DEFAULT 1 CHECK (attempt > 0),
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    input_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    output_summary JSONB,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_agent_steps_run_sequence UNIQUE (run_id, sequence_number),
    CONSTRAINT uq_agent_steps_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT uq_agent_steps_workspace_id_run_id_id UNIQUE (workspace_id, run_id, id),
    CONSTRAINT ck_agent_steps_type CHECK (type IN ('MODEL_CALL', 'TOOL_CALL', 'FINAL_RESPONSE')),
    CONSTRAINT ck_agent_steps_status CHECK (status IN
        ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT')),
    CONSTRAINT fk_agent_steps_run_scope FOREIGN KEY (workspace_id, run_id)
        REFERENCES agent_runs (workspace_id, id) ON DELETE CASCADE
);

CREATE TABLE tool_executions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    run_id UUID NOT NULL,
    step_id UUID NOT NULL,
    tool_call_id VARCHAR(160) NOT NULL,
    tool_name VARCHAR(120) NOT NULL,
    tool_version VARCHAR(40) NOT NULL,
    idempotency_key CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt INTEGER NOT NULL DEFAULT 1 CHECK (attempt > 0),
    arguments_hash CHAR(64) NOT NULL,
    arguments_redacted JSONB NOT NULL DEFAULT '{}'::jsonb,
    observation_summary JSONB,
    error_code VARCHAR(80),
    error_message VARCHAR(500),
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uq_tool_executions_run_call UNIQUE (run_id, tool_call_id),
    CONSTRAINT uq_tool_executions_run_idempotency UNIQUE (run_id, idempotency_key),
    CONSTRAINT ck_tool_executions_status CHECK (status IN
        ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMED_OUT', 'REJECTED')),
    CONSTRAINT fk_tool_executions_run_scope FOREIGN KEY (workspace_id, run_id)
        REFERENCES agent_runs (workspace_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_tool_executions_step_scope FOREIGN KEY (workspace_id, run_id, step_id)
        REFERENCES agent_steps (workspace_id, run_id, id) ON DELETE CASCADE
);

CREATE INDEX idx_agent_steps_run_sequence ON agent_steps (workspace_id, run_id, sequence_number);
CREATE INDEX idx_tool_executions_run_step ON tool_executions (workspace_id, run_id, step_id);
