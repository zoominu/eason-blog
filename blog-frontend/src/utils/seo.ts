/** 站点级 SEO 常量 */
export const SITE_NAME = 'Eason Blog'
export const SITE_DESCRIPTION = '记录 Java、Spring Boot、MySQL 与 Vue 的技术笔记与实践。'

type MetaKey = 'name' | 'property'

/**
 * 写入/更新 meta。
 *
 * 注意必须显式设置属性名：`setAttribute('property', 'og:title')`，
 * 不能把选择器字符串拆开当属性名（会把 og:* 写成 metaproperty，社交平台不识别）。
 */
function upsertMeta(key: MetaKey, value: string, content: string) {
  let el = document.head.querySelector(`meta[${key}="${value}"]`) as HTMLMetaElement | null
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(key, value)
    document.head.appendChild(el)
  }
  el.setAttribute('content', content)
}

function upsertCanonical(url: string) {
  let link = document.head.querySelector('link[rel="canonical"]') as HTMLLinkElement | null
  if (!link) {
    link = document.createElement('link')
    link.rel = 'canonical'
    document.head.appendChild(link)
  }
  link.href = url
}

function removeMeta(key: MetaKey, value: string) {
  document.head.querySelector(`meta[${key}="${value}"]`)?.remove()
}

/** 路由切换时恢复站点默认值，避免上一篇的标题/描述残留 */
export function resetSeo() {
  document.title = SITE_NAME
  upsertMeta('name', 'description', SITE_DESCRIPTION)
  upsertMeta('property', 'og:title', SITE_NAME)
  upsertMeta('property', 'og:description', SITE_DESCRIPTION)
  upsertMeta('property', 'og:type', 'website')
  removeMeta('property', 'article:published_time')
  document.head.querySelector('link[rel="canonical"]')?.remove()
}

export interface ArticleSeoInput {
  title: string
  slug: string
  summary?: string | null
  cover?: string | null
  seoTitle?: string | null
  seoDescription?: string | null
  publishTime?: string | null
}

/** 文章页 SEO：标题、描述、OpenGraph、canonical */
export function applyArticleSeo(article: ArticleSeoInput) {
  const title = article.seoTitle || article.title
  const description = article.seoDescription || article.summary || SITE_DESCRIPTION
  const url = `${window.location.origin}/article/${article.slug}`

  document.title = `${title} · ${SITE_NAME}`
  upsertMeta('name', 'description', description)
  upsertMeta('property', 'og:title', title)
  upsertMeta('property', 'og:description', description)
  upsertMeta('property', 'og:type', 'article')
  upsertMeta('property', 'og:url', url)
  if (article.cover) {
    const image = article.cover.startsWith('http') ? article.cover : window.location.origin + article.cover
    upsertMeta('property', 'og:image', image)
  }
  if (article.publishTime) {
    upsertMeta('property', 'article:published_time', article.publishTime)
  }
  upsertCanonical(url)
}

/** 列表/详情页的简单标题设置 */
export function setPageTitle(title: string) {
  document.title = `${title} · ${SITE_NAME}`
}
