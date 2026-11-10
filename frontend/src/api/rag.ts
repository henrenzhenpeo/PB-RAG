import { openEventStream } from '@/api/sse'
import { http, type ApiResponse } from '@/api/http'

export interface RagCitation {
  index: number
  chunkId: string
  documentId: string
  documentVersionId: string
  documentName: string
  sequenceNumber: number
  sourcePosition: string
  excerpt: string
  similarity: number
}

export interface RagStreamEvent {
  type: 'started' | 'status' | 'citations' | 'delta' | 'completed' | 'error'
  text: string | null
  citations: RagCitation[] | null
  errorCode: string | null
  sessionId: string | null
  messageId: string | null
}

export interface PersistedChatTurn {
  turnId: string
  userMessageId: string
  assistantMessageId: string
  question: string
  answer: string
  status: 'COMPLETED' | 'STREAMING' | 'INTERRUPTED'
  errorCode: string | null
  citations: RagCitation[]
  createdAt: string
}

export interface ChatSession {
  id: string
  workspaceId: string
  knowledgeBaseId: string
  title: string
  turns: PersistedChatTurn[]
  createdAt: string
  updatedAt: string
}

/** 向指定知识库发起带引用的流式 RAG 问答。 */
export async function streamRagAnswer(
  workspaceId: string,
  knowledgeBaseId: string,
  sessionId: string | null,
  question: string,
  signal: AbortSignal,
  onEvent: (event: RagStreamEvent) => void,
) {
  await openEventStream(`/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/rag/stream`, {
    signal,
    method: 'POST',
    body: JSON.stringify({ sessionId, question }),
    headers: { 'Content-Type': 'application/json', 'X-PBRAG-Request': '1' },
    onMessage(data) {
      onEvent(JSON.parse(data) as RagStreamEvent)
    },
  })
}

/** 读取当前账号在指定知识库中的持久化会话。 */
export async function getChatSession(workspaceId: string, knowledgeBaseId: string, sessionId: string) {
  const response = await http.get<ApiResponse<ChatSession>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/chat-sessions/${sessionId}`,
  )
  if (!response.data.success || !response.data.data) throw new Error('无法读取问答会话')
  return response.data.data
}
