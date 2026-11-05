/** 与后端 docs/API.md 一一对应的类型定义，避免页面里散落 any。 */

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

export interface ArticleSummary {
  id: number
  title: string
  slug: string
  summary: string
  cover?: string | null
  categoryId?: number | null
  categoryName?: string | null
  tags: string[]
  top: boolean
  recommend: boolean
  viewCount: number
  likeCount: number
  commentCount: number
  readingMinutes: number
  publishTime: string
}

export interface ArticleDetail extends ArticleSummary {
  categorySlug?: string | null
  tagSlugs: string[]
  content: string
  updateTime: string
  seoTitle?: string | null
  seoDescription?: string | null
  prevSlug?: string | null
  prevTitle?: string | null
  nextSlug?: string | null
  nextTitle?: string | null
}

export interface Category {
  id: number
  parentId: number
  name: string
  slug: string
  description?: string | null
  articleCount: number
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
}

export interface User {
  id: number
  uid: string
  email: string
  username: string
  nickname: string
  avatar?: string | null
  bio?: string | null
  roles: string[]
  emailVerified: boolean
  registerTime: string
}

export interface SiteStatistics {
  articleCount: number
  viewCount: number
  categoryCount: number
  tagCount: number
  columnCount: number
}

export type SiteConfig = Record<string, string>

export interface InteractionState {
  liked: boolean
  favorited: boolean
  likeCount: number
  commentCount: number
}

export interface CommentItem {
  id: number
  userId: number
  nickname: string
  avatar?: string | null
  content: string
  parentId: number
  createTime: string
  mine: boolean
  author: boolean
  /** 被拒绝但有已通过回复：以「当前评论已被隐藏」占位展示 */
  hidden?: boolean
  replies: CommentItem[]
}

/** 个人中心「我的评论」 */
export interface MyComment {
  id: number
  articleId: number
  articleTitle: string
  articleSlug: string | null
  content: string
  status: number
  createTime: string
  canDelete: boolean
}

export interface NotificationItem {
  id: number
  type: string
  title: string
  content?: string | null
  link?: string | null
  read: boolean
  createTime: string
}
