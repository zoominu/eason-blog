export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
  pages: number
}

export interface User {
  id: number
  uid: string
  email: string
  nickname: string
  avatar?: string | null
  bio?: string | null
  roles: string[]
  registerTime: string
}

export interface RecentArticle {
  id: number
  title: string
  status: number
  updateTime: string
}

export interface RecentUser {
  id: number
  nickname: string
  email: string
  avatar?: string | null
  lastLoginTime: string
  lastLoginIp?: string | null
  roles: string[]
}

export interface LoginLogItem {
  id: number
  email: string
  ip?: string | null
  loginStatus: number
  message?: string | null
  createTime: string
}

export interface SystemInfo {
  appName: string
  profile: string
  javaVersion: string
  osName: string
  osArch: string
  startedAt: string
  uptimeSeconds: number
  heapUsedMb: number
  heapMaxMb: number
  dbProduct: string
  dbVersion: string
  serverTime: string
}

export interface DashboardData {
  publishedArticles: number
  draftArticles: number
  offlineArticles: number
  recycledArticles: number
  totalViews: number
  users: number
  categories: number
  tags: number
  recentArticles: RecentArticle[]
  archive: { month: string; total: number }[]
  recentUsers: RecentUser[]
  recentLogins: LoginLogItem[]
  system: SystemInfo
}

/** 0 草稿 1 已发布 2 已下线 3 回收站 */
export type ArticleStatus = 0 | 1 | 2 | 3

export interface AdminArticleListItem {
  id: number
  title: string
  slug: string
  summary?: string | null
  categoryId?: number | null
  categoryName?: string | null
  tags: string[]
  status: ArticleStatus
  visibility: number
  top: boolean
  recommend: boolean
  viewCount: number
  publishTime?: string | null
  updateTime: string
}

export interface AdminArticle {
  id: number
  title: string
  slug: string
  summary?: string | null
  cover?: string | null
  categoryId?: number | null
  tagIds: number[]
  tagNames: string[]
  content: string
  status: ArticleStatus
  visibility: number
  top: boolean
  recommend: boolean
  seoTitle?: string | null
  seoDescription?: string | null
  viewCount: number
  publishTime?: string | null
  createTime: string
  updateTime: string
}

export interface ArticleUpsertPayload {
  title: string
  slug?: string
  categoryId?: number | null
  summary?: string
  cover?: string
  content: string
  visibility: number
  isTop: number
  isRecommend: number
  seoTitle?: string
  seoDescription?: string
  tagIds: number[]
  status?: number
}

export interface Category {
  id: number
  parentId: number
  name: string
  slug: string
  description?: string | null
  articleCount: number
}

export interface AdminUser {
  id: number
  uid: string
  email: string
  username: string
  nickname: string
  avatar?: string | null
  bio?: string | null
  status: number
  roles: string[]
  registerTime: string
  lastLoginTime?: string | null
  lastLoginIp?: string | null
}

export interface RoleItem {
  id: number
  code: string
  name: string
  description?: string | null
  permissionIds: number[]
}

export interface PermissionItem {
  id: number
  code: string
  name: string
  description?: string | null
}

export interface ResourceItem {
  id: number
  originalName: string
  url: string
  mimeType: string
  type: string
  size: number
  sha256?: string | null
  articleCount: number
  createTime: string
}

export interface ResourceRef {
  id: number
  title: string
  slug: string
  status: number
}

export interface AdminLoginLog {
  id: number
  email: string
  ip?: string | null
  loginStatus: number
  message?: string | null
  createTime: string
}

export interface AdminOperationLog {
  id: number
  operatorName?: string | null
  module: string
  action: string
  detail?: string | null
  ip?: string | null
  createTime: string
}

export interface SystemConfigItem {
  id: number
  key: string
  value?: string | null
  description?: string | null
  isPublic: number
}

export interface Tag {
  id: number
  name: string
  slug: string
  articleCount: number
}

export interface Column {
  id: number
  name: string
  slug: string
  description?: string | null
  cover?: string | null
  articleCount: number
  sort?: number
}

/** 导入 Markdown 的解析结果 */
export interface MarkdownImportResult {
  title: string
  slug: string
  summary: string
  cover: string
  content: string
  images: { name: string; url: string }[]
  warnings: string[]
}