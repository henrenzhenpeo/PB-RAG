import { http, type ApiResponse } from './http'

export type RetrievalStage = 'VECTOR' | 'KEYWORD' | 'FUSION' | 'RERANK' | 'CONTEXT'

export interface RetrievalHit {
  chunkId: string
  documentId: string
  documentVersionId: string
  documentName: string
  sequenceNumber: number
  content: string
  sourcePosition: string
  similarity: number
}

export interface RetrievalStageResult {
  stage: RetrievalStage
  rank: number
  score: number
  hit: RetrievalHit
  metadata: Record<string, unknown>
}

export interface RetrievalStageSummary {
  stage: RetrievalStage
  status: 'COMPLETED' | 'SKIPPED' | 'DEGRADED'
  candidateCount: number
  latencyMs: number
  errorCode: string | null
  metadata: Record<string, unknown>
}

export interface RetrievalPipelineResult {
  runId: string
  finalStage: string
  degradationCode: string | null
  hits: RetrievalHit[]
  stages: RetrievalStageResult[]
  stageSummaries: RetrievalStageSummary[]
}

/** 执行一次可追溯检索，返回所有阶段摘要及候选快照。 */
export async function debugRetrieval(workspaceId: string, knowledgeBaseId: string, question: string) {
  const response = await http.post<ApiResponse<RetrievalPipelineResult>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/retrieval/debug`,
    { question },
  )
  if (!response.data.data) throw new Error('后端未返回检索调试结果')
  return response.data.data
}
