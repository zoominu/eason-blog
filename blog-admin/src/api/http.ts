/**
 * 后台请求封装：baseURL=/api/v1、携带 Cookie、统一解包与错误文案。
 */
import axios from 'axios'
import type { ApiResponse } from './types'

const instance = axios.create({
  baseURL: '/api/v1',
  withCredentials: true,
  timeout: 15000
})

instance.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error)) {
      const status = error.response?.status
      const message =
        (error.response?.data as { message?: string } | undefined)?.message ??
        (status === 401 ? '登录已过期，请重新登录' : '请求失败，请稍后重试')
      return Promise.reject(new Error(message))
    }
    return Promise.reject(error)
  }
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const response = await instance.get<ApiResponse<T>>(url, { params })
  return response.data.data
}

export async function post<T>(url: string, body?: unknown): Promise<T> {
  const response = await instance.post<ApiResponse<T>>(url, body ?? {})
  return response.data.data
}

export async function patch<T>(url: string, body?: unknown): Promise<T> {
  const response = await instance.patch<ApiResponse<T>>(url, body ?? {})
  return response.data.data
}

export async function del<T>(url: string): Promise<T> {
  const response = await instance.delete<ApiResponse<T>>(url)
  return response.data.data
}

export default instance
