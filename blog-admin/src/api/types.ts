export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
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

export interface DashboardData {
  publishedArticles: number
  draftArticles: number
  offlineArticles: number
  totalViews: number
  users: number
  categories: number
  tags: number
  recentArticles: RecentArticle[]
  archive: { month: string; total: number }[]
}
