import instance, { del, get, patch, post } from './http'
import type {
  AdminArticle,
  AdminArticleListItem,
  AdminLoginLog,
  AdminOperationLog,
  AdminUser,
  ApiResponse,
  ArticleUpsertPayload,
  Category,
  Column,
  DashboardData,
  PageResult,
  PermissionItem,
  ResourceItem,
  RoleItem,
  SystemConfigItem,
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
  remove: (id: number) => del<void>(`/admin/columns/${id}`),
  articles: (id: number) =>
    get<{ id: number; title: string; status: number; sort: number }[]>(`/admin/columns/${id}/articles`),
  setArticles: (id: number, articleIds: number[]) =>
    instance.put(`/api/v1/admin/columns/${id}/articles`, { articleIds }).then(() => undefined)
}

export interface AdminComment {
  id: number
  articleId: number
  articleTitle: string
  nickname: string
  email: string
  content: string
  status: number
  createTime: string
}

export interface AdminGuestbook {
  id: number
  nickname: string
  email: string
  content: string
  status: number
  createTime: string
}

export const moderationApi = {
  comments: (params: { status?: number; page?: number; size?: number }) =>
    get<PageResult<AdminComment>>('/admin/comments', { ...params }),
  approveComment: (id: number) => post<void>(`/admin/comments/${id}/approve`),
  rejectComment: (id: number) => post<void>(`/admin/comments/${id}/reject`),

  guestbook: (params: { status?: number; page?: number; size?: number }) =>
    get<PageResult<AdminGuestbook>>('/admin/guestbook', { ...params }),
  approveGuestbook: (id: number) => post<void>(`/admin/guestbook/${id}/approve`),
  rejectGuestbook: (id: number) => post<void>(`/admin/guestbook/${id}/reject`)
}

export const userApi = {
  list: (params: { keyword?: string; status?: number; page?: number; size?: number }) =>
    get<PageResult<AdminUser>>('/admin/users', { ...params }),
  disable: (id: number) => post<void>(`/admin/users/${id}/disable`),
  enable: (id: number) => post<void>(`/admin/users/${id}/enable`),
  assignRoles: (id: number, roleIds: number[]) => patch<void>(`/admin/users/${id}/roles`, { roleIds })
}

export const roleApi = {
  list: () => get<RoleItem[]>('/admin/roles'),
  permissions: () => get<PermissionItem[]>('/admin/permissions'),
  updatePermissions: (id: number, permissionIds: number[]) =>
    patch<void>(`/admin/roles/${id}/permissions`, { permissionIds })
}

export const resourceApi = {
  list: (params: { keyword?: string; page?: number; size?: number }) =>
    get<PageResult<ResourceItem>>('/admin/resources', { ...params }),
  upload: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return instance
      .post<ApiResponse<ResourceItem>>('/admin/resources/upload', form, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      .then((res) => res.data.data)
  },
  remove: (id: number) => del<void>(`/admin/resources/${id}`)
}

export const logApi = {
  loginLogs: (params: { status?: number; keyword?: string; page?: number; size?: number }) =>
    get<PageResult<AdminLoginLog>>('/admin/login-logs', { ...params }),
  operationLogs: (params: { module?: string; page?: number; size?: number }) =>
    get<PageResult<AdminOperationLog>>('/admin/operation-logs', { ...params })
}

export const settingApi = {
  list: () => get<SystemConfigItem[]>('/admin/settings'),
  update: (configs: Record<string, string>) => patch<{ updated: number }>('/admin/settings', configs)
}
