package dev.pbrag.retrieval.domain;

/** Production RAG 中可持久化、可回放的检索阶段。 */
public enum RetrievalStage {
	VECTOR,
	KEYWORD,
	FUSION,
	RERANK,
	CONTEXT
}
