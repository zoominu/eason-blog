import { get } from './http'
import type { ArticleSummary, PageResult } from './types'

export interface SearchParams {
  keyword: string
  page?: number
  size?: number
}

/** 站内搜索：标题/摘要/正文，只返回已发布公开文章。 */
export const searchApi = {
  query: (params: SearchParams) => get<PageResult<ArticleSummary>>('/search', { ...params })
}
