import { get, patch, post } from './http'
import type {
  ArticleDetail,
  ArticleSummary,
  Category,
  Column,
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
  categoryArticles: (slug: string, page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>(`/categories/${slug}/articles`, { page, size }),
  tags: () => get<Tag[]>('/tags'),
  tagArticles: (slug: string, page = 1, size = 10) =>
    get<PageResult<ArticleSummary>>(`/tags/${slug}/articles`, { page, size }),
  columns: () => get<Column[]>('/columns'),
  column: (slug: string) => get<Column>(`/columns/${slug}`)
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
    patch<void>('/users/me/password', { oldPassword, newPassword })
}
