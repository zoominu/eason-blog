import { del, get, patch, post } from './http'
import type {
  AdminArticle,
  AdminArticleListItem,
  ArticleUpsertPayload,
  Category,
  Column,
  DashboardData,
  PageResult,
  Tag,
  User
} from './types'

export const authApi = {
  login: (email: string, password: string) => post<User>('/auth/login', { email, password }),
  logout: () => post<void>('/auth/logout'),
  me: () => get<User>('/auth/me')
}

export const dashboardApi = {
  overview: () => get<DashboardData>('/admin/dashboard')
}

export interface ArticleQuery {
  page?: number
  size?: number
  keyword?: string
  status?: number
  categoryId?: number
}

export const articleApi = {
  page: (params: ArticleQuery) => get<PageResult<AdminArticleListItem>>('/admin/articles', { ...params }),
  detail: (id: number) => get<AdminArticle>(`/admin/articles/${id}`),
  create: (payload: ArticleUpsertPayload) => post<{ id: number }>('/admin/articles', payload),
  update: (id: number, payload: ArticleUpsertPayload) => patch<void>(`/admin/articles/${id}`, payload),
  publish: (id: number) => post<void>(`/admin/articles/${id}/publish`),
  offline: (id: number) => post<void>(`/admin/articles/${id}/offline`),
  restore: (id: number) => post<void>(`/admin/articles/${id}/restore`),
  remove: (id: number) => del<void>(`/admin/articles/${id}`)
}

export const categoryApi = {
  list: () => get<Category[]>('/admin/categories'),
  create: (payload: { name: string; slug?: string; parentId?: number; description?: string; sort?: number }) =>
    post<{ id: number }>('/admin/categories', payload),
  update: (id: number, payload: { name: string; slug?: string; parentId?: number; description?: string; sort?: number }) =>
    patch<void>(`/admin/categories/${id}`, payload),
  remove: (id: number) => del<void>(`/admin/categories/${id}`)
}

export const tagApi = {
  list: () => get<Tag[]>('/admin/tags'),
  create: (payload: { name: string; slug?: string }) => post<{ id: number }>('/admin/tags', payload),
  update: (id: number, payload: { name: string; slug?: string }) => patch<void>(`/admin/tags/${id}`, payload),
  remove: (id: number) => del<void>(`/admin/tags/${id}`)
}

export const columnApi = {
  list: () => get<Column[]>('/admin/columns'),
  create: (payload: { name: string; slug?: string; description?: string; cover?: string; sort?: number }) =>
    post<{ id: number }>('/admin/columns', payload),
  update: (id: number, payload: { name: string; slug?: string; description?: string; cover?: string; sort?: number }) =>
    patch<void>(`/admin/columns/${id}`, payload),
  remove: (id: number) => del<void>(`/admin/columns/${id}`)
}
