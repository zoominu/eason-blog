import { del, get, patch, post } from './http'
import type {
  ArticleDetail,
  ArticleSummary,
  Category,
  Column,
  CommentItem,
  InteractionState,
  MyComment,
  NotificationItem,
  PageResult,
  SiteConfig,
  SiteStatistics,
  Tag,
  User
} from './types'

export interface ArticleListParams {
  page?: number
  size?: number
  keyword?: string
  category?: string
  sort?: 'latest' | 'popular'
}

export const articleApi = {
  page: (params: ArticleListParams) => get<PageResult<ArticleSummary>>('/articles', { ...params }),
  latest: (limit = 5) => get<ArticleSummary[]>('/articles/latest', { limit }),
  recommended: (limit = 5) => get<ArticleSummary[]>('/articles/recommended', { limit }),
  detail: (slug: string) => get<ArticleDetail>(`/articles/${slug}`)
}

export const taxonomyApi = {
  categories: () => get<Category[]>('/categories'),
  category: (slug: string) => get<Category>(`/categories/${slug}`),
  archive: () => get<{ month: string; total: number }[]>('/archive'),
  categoryArticles: (slug: string, page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>(`/categories/${slug}/articles`, { page, size }),
  tags: () => get<Tag[]>('/tags'),
  tagArticles: (slug: string, page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>(`/tags/${slug}/articles`, { page, size }),
  columns: () => get<Column[]>('/columns'),
  column: (slug: string) => get<Column>(`/columns/${slug}`),
  columnArticles: (slug: string) => get<ArticleSummary[]>(`/columns/${slug}/articles`)
}

export const siteApi = {
  config: () => get<SiteConfig>('/site/config'),
  statistics: () => get<SiteStatistics>('/site/statistics')
}

export const authApi = {
  login: (email: string, password: string) => post<User>('/auth/login', { email, password }),
  register: (payload: { email: string; password: string; nickname?: string }) =>
    post<User>('/auth/register', payload),
  logout: () => post<void>('/auth/logout'),
  me: () => get<User>('/auth/me'),
  updateProfile: (payload: { nickname?: string; avatar?: string; bio?: string }) =>
    patch<User>('/users/me', payload),
  changePassword: (oldPassword: string, newPassword: string) =>
    patch<void>('/users/me/password', { oldPassword, newPassword }),
  uploadAvatar: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<{ url: string }>('/users/me/avatar', form)
  },
  forgotPassword: (email: string) => post<void>('/auth/forgot-password', { email }),
  resetPassword: (token: string, newPassword: string) =>
    post<void>('/auth/reset-password', { token, newPassword }),
  verifyEmail: (token: string) => post<void>('/auth/verify-email', { token }),
  resendVerification: () => post<void>('/auth/resend-verification')
}

/** 互动：评论、点赞、收藏、收藏列表、通知、留言 */
export const interactionApi = {
  status: (articleId: number) => get<InteractionState>(`/articles/${articleId}/interaction`),
  like: (articleId: number) => post<InteractionState>(`/articles/${articleId}/like`),
  unlike: (articleId: number) => del<InteractionState>(`/articles/${articleId}/like`),
  favorite: (articleId: number) => post<InteractionState>(`/articles/${articleId}/favorite`),
  unfavorite: (articleId: number) => del<InteractionState>(`/articles/${articleId}/favorite`),

  comments: (slug: string, page = 1, size = 10) =>
    get<PageResult<CommentItem>>(`/articles/${slug}/comments`, { page, size }),
  addComment: (articleId: number, content: string, parentId?: number | null) =>
    post<{ id: number; status: number }>(`/articles/${articleId}/comments`, { content, parentId }),

  favorites: (page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>('/users/me/favorites', { page, size }),
  removeFavorites: (articleIds: number[]) =>
    post<{ removed: number }>('/users/me/favorites/batch-remove', { articleIds }),
  likes: (page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>('/users/me/likes', { page, size }),
  removeLikes: (articleIds: number[]) =>
    post<{ removed: number }>('/users/me/likes/batch-remove', { articleIds }),
  myComments: (page = 1, size = 10) =>
    get<PageResult<MyComment>>('/users/me/comments', { page, size }),
  deleteMyComment: (id: number) => del<void>(`/users/me/comments/${id}`),
  deleteMyComments: (ids: number[]) =>
    post<{ deleted: number }>('/users/me/comments/batch-delete', { ids }),
  notifications: (limit = 20, type?: string) =>
    get<NotificationItem[]>('/users/me/notifications', { limit, type }),
  unreadCount: () => get<{ count: number }>('/users/me/notifications/unread-count'),
  markRead: (id: number) => patch<void>(`/users/me/notifications/${id}/read`),
  markAllRead: () => patch<{ updated: number }>('/users/me/notifications/read-all'),
  deleteRead: () => del<{ deleted: number }>('/users/me/notifications/read'),

  submitGuestbook: (content: string) => post<void>('/guestbook', { content })
}
