import axios from 'axios'

// 统一 HTTP 客户端；自定义请求头配合后端过滤器阻止普通跨站表单写请求。
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

/** 将 Axios、后端统一错误结构和普通异常收敛为可直接展示的用户消息。 */
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
