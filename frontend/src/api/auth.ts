import { http, type ApiResponse } from './http'

export interface User { id: string; email: string; displayName: string }

export async function currentUser(): Promise<User> {
  const response = await http.get<ApiResponse<User>>('/auth/me')
  if (!response.data.data) throw new Error('用户信息不存在')
  return response.data.data
}

export async function login(email: string, password: string): Promise<User> {
  const response = await http.post<ApiResponse<User>>('/auth/login', { email, password })
  if (!response.data.data) throw new Error('登录失败')
  return response.data.data
}

export async function register(email: string, displayName: string, password: string): Promise<User> {
  const response = await http.post<ApiResponse<User>>('/auth/register', { email, displayName, password })
  if (!response.data.data) throw new Error('注册失败')
  return response.data.data
}

export async function updateProfile(displayName: string): Promise<User> {
  const response = await http.put<ApiResponse<User>>('/auth/me', { displayName })
  if (!response.data.data) throw new Error('更新失败')
  return response.data.data
}

export async function logout(): Promise<void> { await http.post('/auth/logout') }
