import { http, type ApiResponse } from './http'

export interface KnowledgeBase {
  id: string
  workspaceId: string
  name: string
  description: string | null
}

export interface CreateKnowledgeBaseInput {
  name: string
  description?: string
}

export async function listKnowledgeBases(workspaceId: string): Promise<KnowledgeBase[]> {
  const response = await http.get<ApiResponse<KnowledgeBase[]>>(
    `/workspaces/${workspaceId}/knowledge-bases`,
  )
  return response.data.data ?? []
}

export async function getKnowledgeBase(
  workspaceId: string,
  knowledgeBaseId: string,
): Promise<KnowledgeBase> {
  const response = await http.get<ApiResponse<KnowledgeBase>>(
    `/workspaces/${workspaceId}/knowledge-bases/${knowledgeBaseId}`,
  )
  if (!response.data.data) throw new Error('后端未返回知识库数据')
  return response.data.data
}

export async function createKnowledgeBase(
  workspaceId: string,
  input: CreateKnowledgeBaseInput,
): Promise<KnowledgeBase> {
  const response = await http.post<ApiResponse<KnowledgeBase>>(
    `/workspaces/${workspaceId}/knowledge-bases`,
    input,
  )
  if (!response.data.data) throw new Error('后端未返回知识库数据')
  return response.data.data
}
