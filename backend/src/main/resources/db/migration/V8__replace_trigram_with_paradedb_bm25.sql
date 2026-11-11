CREATE EXTENSION IF NOT EXISTS pg_search;

DROP INDEX IF EXISTS idx_chunks_content_trgm;

CREATE INDEX idx_chunks_content_bm25
    ON chunks
    USING bm25 (id, workspace_id, document_version_id, content)
    WITH (
        key_field = 'id',
        text_fields = '{"content":{"tokenizer":{"type":"chinese_lindera"}}}'
    );

