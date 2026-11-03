import axios, { type AxiosRequestConfig } from 'axios'
import type { ApiResponse } from './types'

/**
 * 统一请求封装。
 *
 * 约定：
 *  - baseURL 固定 /api/v1，开发期由 Vite 代理，生产由 Nginx 反代；
 *  - withCredentials 必须开启，否则 HttpOnly Cookie 不会携带；
 *  - 业务错误统一抛出 Error(message)，由页面决定展示方式。
 */
const instance = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 15000
})

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error)) {
      const message =
        (error.response?.data as { message?: string } | undefined)?.message ??
        (error.code === 'ECONNABORTED' ? '请求超时，请稍后重试' : '网络异常，请检查后端服务是否启动')
      return Promise.reject(new Error(message))
    }
    return Promise.reject(error)
  }
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await instance.get<ApiResponse<T>>(url, { params })
  return response.data.data
}

export async function post<T>(url: string, body?: unknown, config?: AxiosRequestConfig): Promise<T> {
  const response = await instance.post<ApiResponse<T>>(url, body ?? {}, config)
  return response.data.data
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const response = await instance.patch<ApiResponse<T>>(url, body ?? {})
  return response.data.data
}

export default instance
