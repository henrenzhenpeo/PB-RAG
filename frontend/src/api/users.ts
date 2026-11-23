import { http, type ApiResponse } from './http'

export interface ManagedUser {
  id: string
  email: string
  displayName: string
}

export interface UserInput {
  email: string
  displayName: string
  password?: string
}

export async function listUsers(): Promise<ManagedUser[]> {
  const response = await http.get<ApiResponse<ManagedUser[]>>('/users')
  return response.data.data ?? []
}

export async function createUser(input: UserInput & { password: string }): Promise<ManagedUser> {
  const response = await http.post<ApiResponse<ManagedUser>>('/users', input)
  if (!response.data.data) throw new Error('后端未返回用户数据')
  return response.data.data
}

export async function updateUser(id: string, input: UserInput): Promise<ManagedUser> {
  const response = await http.put<ApiResponse<ManagedUser>>(`/users/${id}`, input)
  if (!response.data.data) throw new Error('后端未返回用户数据')
  return response.data.data
}

export async function deleteUser(id: string): Promise<void> {
  await http.delete(`/users/${id}`)
}
