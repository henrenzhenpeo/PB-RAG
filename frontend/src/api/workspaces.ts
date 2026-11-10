import { http, type ApiResponse } from './http'

// Workspace API 适配层；组件不直接处理后端 ApiResponse 包装结构。
export interface Workspace {
  id: string
  name: string
}

export async function listWorkspaces(): Promise<Workspace[]> {
  const response = await http.get<ApiResponse<Workspace[]>>('/workspaces')
  return response.data.data ?? []
}

export async function createWorkspace(name: string): Promise<Workspace> {
  const response = await http.post<ApiResponse<Workspace>>('/workspaces', { name })
  if (!response.data.data) throw new Error('后端未返回 Workspace 数据')
  return response.data.data
}
