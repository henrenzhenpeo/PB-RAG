-- qwen3.7-text-embedding-flash 默认输出 1024 维。先移除依赖旧维度的索引，再调整列并重建。
-- 当前阶段尚未接入真实 Embedding；若库中已有 1536 维数据，转换会明确失败，避免静默丢失索引数据。
DROP INDEX IF EXISTS idx_chunks_embedding_hnsw;

ALTER TABLE chunks
    ALTER COLUMN embedding TYPE vector(1024)
    USING embedding::vector(1024);

CREATE INDEX idx_chunks_embedding_hnsw
    ON chunks
    USING hnsw (embedding vector_cosine_ops)
    WHERE embedding IS NOT NULL;
