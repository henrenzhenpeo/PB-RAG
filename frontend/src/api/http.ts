import axios from 'axios'

export const http = axios.create({
  baseURL: '/api',
  timeout: 15_000,
  headers: { 'X-PBRAG-Request': '1' },
})

http.interceptors.response.use((response) => response, (error: unknown) => {
  if (axios.isAxiosError(error) && error.response?.status === 401 && !window.location.pathname.startsWith('/sign-in')) {
    const currentPath = `${window.location.pathname}${window.location.search}${window.location.hash}`
    window.location.assign(`/sign-in?redirect=${encodeURIComponent(currentPath)}`)
  }
  return Promise.reject(error)
})

export interface ApiFieldError {
  field: string
  message: string
}

export interface ApiError {
  code: string
  message: string
  fieldErrors: ApiFieldError[]
}

export interface ApiResponse<T> {
  success: boolean
  data: T | null
  error: ApiError | null
  timestamp: string
}

export function errorMessage(error: unknown): string {
  if (axios.isAxiosError<ApiResponse<unknown>>(error)) {
    const apiError = error.response?.data?.error
    if (apiError?.fieldErrors?.length) {
      return apiError.fieldErrors.map((item) => item.message).join('；')
    }
    return apiError?.message ?? (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '无法连接后端服务')
  }
  return error instanceof Error ? error.message : '发生未知错误'
}
