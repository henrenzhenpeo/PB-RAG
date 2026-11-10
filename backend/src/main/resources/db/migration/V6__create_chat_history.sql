CREATE TABLE chat_sessions (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    knowledge_base_id UUID NOT NULL,
    user_id UUID NOT NULL REFERENCES app_users (id) ON DELETE CASCADE,
    title VARCHAR(160) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_sessions_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT fk_chat_sessions_knowledge_base_scope
        FOREIGN KEY (workspace_id, knowledge_base_id)
        REFERENCES knowledge_bases (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_sessions_user_knowledge_updated
    ON chat_sessions (user_id, workspace_id, knowledge_base_id, updated_at DESC);

CREATE TABLE chat_messages (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    session_id UUID NOT NULL,
    turn_id UUID NOT NULL,
    role VARCHAR(16) NOT NULL,
    status VARCHAR(24) NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_chat_messages_workspace_id_id UNIQUE (workspace_id, id),
    CONSTRAINT uq_chat_messages_turn_role UNIQUE (session_id, turn_id, role),
    CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_chat_messages_status CHECK (status IN ('COMPLETED', 'STREAMING', 'INTERRUPTED')),
    CONSTRAINT fk_chat_messages_session_scope
        FOREIGN KEY (workspace_id, session_id)
        REFERENCES chat_sessions (workspace_id, id)
        ON DELETE CASCADE
);

CREATE INDEX idx_chat_messages_session_created
    ON chat_messages (session_id, created_at, id);

CREATE TABLE message_citations (
    id UUID PRIMARY KEY,
    workspace_id UUID NOT NULL,
    message_id UUID NOT NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal > 0),
    chunk_id UUID NOT NULL,
    document_id UUID NOT NULL,
    document_version_id UUID NOT NULL,
    document_name VARCHAR(255) NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number >= 0),
    source_position JSONB NOT NULL,
    excerpt TEXT NOT NULL,
    similarity DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_message_citations_message_ordinal UNIQUE (message_id, ordinal),
    CONSTRAINT fk_message_citations_message_scope
        FOREIGN KEY (workspace_id, message_id)
        REFERENCES chat_messages (workspace_id, id)
        ON DELETE CASCADE,
    CONSTRAINT fk_message_citations_chunk_scope
        FOREIGN KEY (workspace_id, chunk_id)
        REFERENCES chunks (workspace_id, id),
    CONSTRAINT fk_message_citations_version_scope
        FOREIGN KEY (workspace_id, document_version_id)
        REFERENCES document_versions (workspace_id, id)
);

CREATE INDEX idx_message_citations_message
    ON message_citations (message_id, ordinal);
