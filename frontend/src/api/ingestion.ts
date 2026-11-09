import { http, type ApiResponse } from './http'

export type IngestionStatus = 'PENDING' | 'RUNNING' | 'RETRY_WAIT' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type IngestionStage = 'PENDING' | 'PARSING' | 'CHUNKING' | 'EMBEDDING' | 'INDEXING' | 'COMPLETED'

export interface IngestionJob {
  id: string
  workspaceId: string
  documentVersionId: string
  status: IngestionStatus
  stage: IngestionStage
  attemptCount: number
  maxAttempts: number
  errorCode: string | null
  errorMessage: string | null
  availableAt: string
  createdAt: string
  updatedAt: string
}

export interface UploadDocumentResult {
  documentId: string
  documentVersionId: string
  jobId: string
  contentSha256: string
  duplicate: boolean
  job: IngestionJob
}

export interface KnowledgeDocument {
  id: string
  workspaceId: string
  knowledgeBaseId: string
  displayName: string
  mediaType: string
  currentVersion: {
    id: string
    versionNumber: number
    contentSha256: string
    createdAt: string
  }
  ingestionJob: IngestionJob | null
  createdAt: string
  updatedAt: string
}

export async function listDocuments(
  workspaceId: string,
  knowledgeBaseId: string,
): Promise<KnowledgeDocument[]> {
  const response = await http.get<ApiResponse<KnowledgeDocument[]>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/documents`,
  )
  return response.data.data ?? []
}

export async function uploadMarkdown(
  workspaceId: string,
  knowledgeBaseId: string,
  file: File,
): Promise<UploadDocumentResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResponse<UploadDocumentResult>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/documents`,
    form,
  )
  if (!response.data.data) throw new Error('后端未返回上传结果')
  return response.data.data
}

export async function getIngestionJob(workspaceId: string, jobId: string): Promise<IngestionJob> {
  const response = await http.get<ApiResponse<IngestionJob>>(
    `/workspaces/${workspaceId}/ingestion-jobs/${jobId}`,
  )
  if (!response.data.data) throw new Error('后端未返回入库任务')
  return response.data.data
}
