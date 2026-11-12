import { http, type ApiResponse } from './http'

// status 表示任务生命周期，stage 表示入库流水线的具体处理位置。
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

/** 使用 multipart 上传原文件；后端快速返回任务，页面随后通过 Job API 轮询进度。 */
export async function uploadDocument(
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

/** 为已有逻辑文档上传新内容版本；旧版本在新任务完成前继续参与检索。 */
export async function uploadDocumentVersion(
  workspaceId: string,
  knowledgeBaseId: string,
  documentId: string,
  file: File,
): Promise<UploadDocumentResult> {
  const form = new FormData()
  form.append('file', file)
  const response = await http.post<ApiResponse<UploadDocumentResult>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}/documents/${documentId}/versions`,
    form,
  )
  if (!response.data.data) throw new Error('后端未返回版本上传结果')
  return response.data.data
}

export async function getIngestionJob(workspaceId: string, jobId: string): Promise<IngestionJob> {
  const response = await http.get<ApiResponse<IngestionJob>>(
    `/workspaces/${workspaceId}/ingestion-jobs/${jobId}`,
  )
  if (!response.data.data) throw new Error('后端未返回入库任务')
  return response.data.data
}

/** 主动取消未完成的入库任务，并返回数据库中的取消终态。 */
export async function cancelIngestionJob(workspaceId: string, jobId: string): Promise<IngestionJob> {
  const response = await http.post<ApiResponse<IngestionJob>>(
    `/workspaces/${workspaceId}/ingestion-jobs/${jobId}/cancel`,
  )
  if (!response.data.data) throw new Error('后端未返回取消结果')
  return response.data.data
}
