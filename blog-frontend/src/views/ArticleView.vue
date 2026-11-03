<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
import java from 'highlight.js/lib/languages/java'
import sql from 'highlight.js/lib/languages/sql'
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import bash from 'highlight.js/lib/languages/bash'
import json from 'highlight.js/lib/languages/json'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import {
  ArrowLeft,
  Bookmark,
  Calendar,
  Clock,
  Copy,
  Eye,
  Heart,
  MessageSquare,
  Send,
  Share2
} from 'lucide-vue-next'
import { articleApi, interactionApi, siteApi } from '@/api'
import type { ArticleDetail, CommentItem, InteractionState } from '@/api/types'
import CommentNode from '@/components/CommentNode.vue'
import Pagination from '@/components/Pagination.vue'
import { useUserStore } from '@/stores/user'
import { formatDate, formatCount } from '@/utils/format'
import { applyArticleSeo, SITE_NAME } from '@/utils/seo'

hljs.registerLanguage('java', java)
hljs.registerLanguage('sql', sql)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('bash', bash)
hljs.registerLanguage('json', json)
hljs.registerLanguage('xml', xml)
hljs.registerLanguage('css', css)

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const article = ref<ArticleDetail | null>(null)
const html = ref('')
const headings = ref<{ id: string; text: string; level: number }[]>([])
const loading = ref(true)
const error = ref('')
const progress = ref(0)
const contentRef = ref<HTMLElement | null>(null)

const interaction = ref<InteractionState>({ liked: false, favorited: false, likeCount: 0, commentCount: 0 })
const comments = ref<CommentItem[]>([])
const commentsPage = ref(1)
const commentsPages = ref(0)
const commentsError = ref('')
const commentText = ref('')
const replyTo = ref<CommentItem | null>(null)
const submitting = ref(false)
const commentHint = ref('')
const lightbox = ref<string | null>(null)
const siteConfig = ref<Record<string, string>>({})

marked.setOptions({ gfm: true, breaks: false })

const readingLabel = computed(() => (article.value ? `${article.value.readingMinutes} 分钟` : ''))
/** 评论数统一用互动接口的口径（含回复），避免与标题数字不一致 */
const commentTotal = computed(() => (interaction.value.commentCount > 0
  ? interaction.value.commentCount
  : comments.value.length))

async function load(slug: string) {
  loading.value = true
  error.value = ''
  headings.value = []
  comments.value = []
  commentsError.value = ''

  // 正文与附属数据分开处理：互动/评论失败不应把整篇文章变成错误页
  let data: ArticleDetail
  try {
    data = await articleApi.detail(slug)
  } catch (e) {
    error.value = (e as Error).message
    loading.value = false
    return
  }

  article.value = data
  html.value = DOMPurify.sanitize(marked.parse(data.content) as string, {
    USE_PROFILES: { html: true },
    ADD_ATTR: ['target']
  })
  loading.value = false
  await nextTick()
  buildToc()
  highlightCode()
  addCopyButtons()
  bindImageLightbox()
  applyArticleSeo(data)
  window.scrollTo({ top: 0 })

  // 以下两个请求互不影响，失败时降级为空状态
  try {
    interaction.value = await interactionApi.status(data.id)
  } catch {
    interaction.value = {
      liked: false,
      favorited: false,
      likeCount: data.likeCount,
      commentCount: data.commentCount
    }
  }
  try {
    await loadComments(1)
  } catch (e) {
    commentsError.value = (e as Error).message
  }

  // 通知里带 ?comment=ID：定位并高亮该条评论
  const targetComment = route.query.comment
  if (targetComment) {
    await nextTick()
    focusComment(String(targetComment))
  }
}

async function loadComments(page: number) {
  if (!article.value) return
  const result = await interactionApi.comments(String(route.params.slug), page, 10)
  comments.value = result.records
  commentsPage.value = result.current
  commentsPages.value = result.pages
  if (result.current !== 1) {
    document.getElementById('comments')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

/**
 * 滚动到指定评论并短暂高亮。
 * 评论可能在其它分页：先找当前页，找不到就逐页找（最多 10 页）。
 */
async function focusComment(commentId: string) {
  let element = document.getElementById(`comment-${commentId}`)
  let page = commentsPage.value
  while (!element && page < Math.min(commentsPages.value, 10)) {
    page += 1
    await loadComments(page)
    await nextTick()
    element = document.getElementById(`comment-${commentId}`)
  }
  if (!element) {
    commentHint.value = '未找到该评论，可能尚未通过审核'
    return
  }
  element.scrollIntoView({ behavior: 'smooth', block: 'center' })
  element.classList.add('comment--highlight')
  setTimeout(() => element?.classList.remove('comment--highlight'), 3200)
}

function highlightCode() {
  const container = contentRef.value
  if (!container) return
  container.querySelectorAll('pre code').forEach((block) => {
    const el = block as HTMLElement
    if (el.dataset.highlighted === 'yes') return
    if (!el.className.includes('language-')) return
    try {
      hljs.highlightElement(el)
    } catch {
      // 语言未注册时忽略
    }
  })
}

/** 给每个代码块加「复制」按钮 */
function addCopyButtons() {
  const container = contentRef.value
  if (!container) return
  container.querySelectorAll('pre').forEach((pre) => {
    if (pre.querySelector('.code-copy')) return
    const button = document.createElement('button')
    button.className = 'code-copy'
    button.type = 'button'
    button.textContent = '复制'
    button.addEventListener('click', async (event) => {
      event.preventDefault()
      event.stopPropagation()
      const code = pre.querySelector('code')?.textContent ?? ''
      try {
        await copyText(code)
        button.textContent = '已复制'
      } catch {
        // 两种方式都被浏览器拒绝时，选中代码让用户手动 Ctrl+C，而不是只说“失败”
        selectText(pre.querySelector('code'))
        button.textContent = '已全选'
      }
      setTimeout(() => (button.textContent = '复制'), 1800)
    })
    pre.appendChild(button)
  })
}

/**
 * 复制文本。
 *
 * 优先 Clipboard API；但它只在“安全上下文”可用（HTTPS 或 localhost），
 * 用局域网 IP 访问、或浏览器拒绝权限时 writeText 会抛错，必须回退到
 * 临时 textarea + execCommand，否则就是“点了没反应”。
 */
async function copyText(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // 继续走兜底方案
    }
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  textarea.setSelectionRange(0, text.length)
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!ok) {
    throw new Error('copy failed')
  }
}

/** 复制失败时的最后兜底：选中代码块内容 */
function selectText(node: Element | null) {
  if (!node) return
  const range = document.createRange()
  range.selectNodeContents(node)
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(range)
}

/** 正文图片点击放大 */
function bindImageLightbox() {
  const container = contentRef.value
  if (!container) return
  container.querySelectorAll('img').forEach((img) => {
    img.classList.add('prose-img')
    img.addEventListener('click', () => {
      lightbox.value = (img as HTMLImageElement).currentSrc || (img as HTMLImageElement).src
    })
  })
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    lightbox.value = null
  }
}

function buildToc() {
  const container = contentRef.value
  if (!container) {
    headings.value = []
    return
  }
  const used = new Set<string>()
  const nodes = Array.from(container.querySelectorAll('h2, h3'))
  headings.value = nodes.map((node, index) => {
    let id = `h-${index}-${node.textContent?.slice(0, 20).replace(/\s+/g, '-') ?? ''}`
    while (used.has(id)) id = `${id}-x`
    used.add(id)
    node.setAttribute('id', id)
    return { id, text: node.textContent ?? '', level: Number(node.tagName.substring(1)) }
  })
  container.querySelectorAll('a[href^="http"]').forEach((link) => {
    link.setAttribute('target', '_blank')
    link.setAttribute('rel', 'noopener noreferrer')
  })
}

function scrollTo(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onScroll() {
  const el = document.documentElement
  const total = el.scrollHeight - el.clientHeight
  progress.value = total > 0 ? Math.min(100, (el.scrollTop / total) * 100) : 0
}

function requireLogin(): boolean {
  if (!userStore.isLoggedIn) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function toggleLike() {
  if (!requireLogin() || !article.value) return
  try {
    interaction.value = interaction.value.liked
      ? await interactionApi.unlike(article.value.id)
      : await interactionApi.like(article.value.id)
  } catch (e) {
    commentHint.value = (e as Error).message
  }
}

async function toggleFavorite() {
  if (!requireLogin() || !article.value) return
  try {
    interaction.value = interaction.value.favorited
      ? await interactionApi.unfavorite(article.value.id)
      : await interactionApi.favorite(article.value.id)
  } catch (e) {
    commentHint.value = (e as Error).message
  }
}

async function share() {
  const url = window.location.href
  const title = article.value?.title ?? SITE_NAME
  const text = buildShareText(url)

  // 系统分享优先（移动端），桌面端一般不可用
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url })
      return
    } catch {
      // 用户取消，继续走复制
    }
  }
  try {
    await copyText(text)
    commentHint.value = '分享文案已复制，可直接粘贴'
  } catch {
    commentHint.value = text
  }
}

/**
 * 分享文案：模板由后台「系统设置 → share.template」维护，
 * 支持 {title} / {url} / {summary}，缺省时回退到内置文案。
 */
function buildShareText(url: string) {
  const template = siteConfig.value['share.template']
    || '推荐一篇好文：{title}\n{url}'
  return template
    .replaceAll('{title}', article.value?.title ?? SITE_NAME)
    .replaceAll('{url}', url)
    .replaceAll('{summary}', article.value?.summary ?? '')
}

function startReply(comment: CommentItem) {
  if (!requireLogin()) return
  replyTo.value = comment
  commentText.value = `@${comment.nickname} `
  document.getElementById('comment-form')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

function cancelReply() {
  replyTo.value = null
  commentText.value = ''
}

async function submitComment() {
  if (!requireLogin() || !article.value) return
  const content = commentText.value.trim()
  if (!content) return
  submitting.value = true
  commentHint.value = ''
  try {
    await interactionApi.addComment(article.value.id, content, replyTo.value?.id ?? null)
    commentText.value = ''
    replyTo.value = null
    commentHint.value = '评论已提交，审核通过后展示'
  } catch (e) {
    commentHint.value = (e as Error).message
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  load(String(route.params.slug))
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('keydown', onKeydown)
  try {
    siteConfig.value = await siteApi.config()
  } catch {
    // 配置拿不到时用内置分享文案
  }
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('keydown', onKeydown)
})

watch(
  () => route.params.slug,
  (slug) => {
    if (slug) {
      replyTo.value = null
      commentHint.value = ''
      load(String(slug))
    }
  }
)
</script>

<template>
  <div class="progress" :style="{ width: `${progress}%` }" />

  <div class="container article">
    <RouterLink to="/blog" class="back">
      <ArrowLeft :size="15" /> 返回博客
    </RouterLink>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-else-if="loading" class="card skeleton" />

    <template v-else-if="article">
      <header class="head">
        <div class="head__meta">
          <RouterLink
            v-if="article.categoryName && article.categorySlug"
            :to="`/category/${article.categorySlug}`"
            class="tag-chip"
          >
            {{ article.categoryName }}
          </RouterLink>
          <span class="muted">{{ formatDate(article.publishTime) }}</span>
        </div>
        <h1>{{ article.title }}</h1>
        <p class="summary">{{ article.summary }}</p>

        <img v-if="article.cover" :src="article.cover" class="cover" :alt="article.title" />

        <div class="meta muted">
          <span><Calendar :size="14" /> {{ formatDate(article.publishTime) }}</span>
          <span><Clock :size="14" /> {{ readingLabel }}</span>
          <span><Eye :size="14" /> {{ formatCount(article.viewCount) }} 次阅读</span>
        </div>
      </header>

      <div class="body">
        <article ref="contentRef" class="prose-blog" v-html="html" />

        <aside v-if="headings.length" class="toc">
          <p class="toc__title">目录</p>
          <button
            v-for="heading in headings"
            :key="heading.id"
            class="toc__item"
            :class="{ 'toc__item--sub': heading.level === 3 }"
            @click="scrollTo(heading.id)"
          >
            {{ heading.text }}
          </button>
        </aside>
      </div>

      <footer class="foot">
        <div v-if="article.tags.length" class="tags">
          <RouterLink
            v-for="(tag, index) in article.tags"
            :key="tag"
            :to="`/tag/${article.tagSlugs[index] ?? tag}`"
            class="tag-chip tag-link"
          >
            # {{ tag }}
          </RouterLink>
        </div>

        <div class="actions">
          <button class="action" :class="{ 'action--on': interaction.liked }" @click="toggleLike">
            <Heart :size="16" :fill="interaction.liked ? 'currentColor' : 'none'" />
            {{ interaction.liked ? '已点赞' : '点赞' }} {{ interaction.likeCount }}
          </button>
          <button class="action" :class="{ 'action--on': interaction.favorited }" @click="toggleFavorite">
            <Bookmark :size="16" :fill="interaction.favorited ? 'currentColor' : 'none'" />
            {{ interaction.favorited ? '已收藏' : '收藏' }}
          </button>
          <a class="action" href="#comments">
            <MessageSquare :size="16" /> 评论 {{ commentTotal }}
          </a>
          <button class="action" @click="share">
            <Share2 :size="16" /> 分享
          </button>
        </div>

        <p class="muted updated">最后更新：{{ formatDate(article.updateTime) }}</p>
      </footer>

      <nav class="neighbours">
        <RouterLink v-if="article.prevSlug" :to="`/article/${article.prevSlug}`" class="neighbour">
          <span class="neighbour__label">上一篇</span>
          <span class="neighbour__title">{{ article.prevTitle }}</span>
        </RouterLink>
        <span v-else class="neighbour neighbour--empty" />
        <RouterLink
          v-if="article.nextSlug"
          :to="`/article/${article.nextSlug}`"
          class="neighbour neighbour--next"
        >
          <span class="neighbour__label">下一篇</span>
          <span class="neighbour__title">{{ article.nextTitle }}</span>
        </RouterLink>
      </nav>

      <section id="comments" class="comments">
        <h2 class="comments__title">评论 {{ commentTotal ? `(${commentTotal})` : '' }}</h2>

        <p v-if="commentsError" class="muted comments__empty">
          评论加载失败：{{ commentsError }}（正文不受影响）
        </p>
        <div v-else-if="comments.length" class="comment-list">
          <CommentNode
            v-for="comment in comments"
            :key="comment.id"
            :comment="comment"
            :depth="0"
            @reply="startReply"
          />
        </div>
        <p v-else class="muted comments__empty">还没有评论，来说点什么吧。</p>

        <Pagination
          v-if="commentsPages > 1"
          :current="commentsPage"
          :pages="commentsPages"
          @change="loadComments"
        />

        <div id="comment-form" class="form">
          <template v-if="userStore.isLoggedIn">
            <p v-if="replyTo" class="form__reply">
              回复 @{{ replyTo.nickname }}
              <button class="link-btn" @click="cancelReply">取消</button>
            </p>
            <textarea
              v-model="commentText"
              class="form__input"
              rows="4"
              maxlength="2000"
              placeholder="友善交流，评论审核通过后展示…"
            />
            <div class="form__foot">
              <span class="muted">{{ commentHint || `${commentText.length}/2000` }}</span>
              <button class="btn btn-primary" :disabled="submitting || !commentText.trim()" @click="submitComment">
                <Send :size="15" /> {{ submitting ? '提交中…' : '发表评论' }}
              </button>
            </div>
          </template>
          <p v-else class="muted">
            <RouterLink :to="{ path: '/login', query: { redirect: route.fullPath } }" class="link">登录</RouterLink>
            后参与评论。
          </p>
        </div>
      </section>
    </template>
  </div>

  <!-- 图片灯箱 -->
  <Teleport to="body">
    <div v-if="lightbox" class="lightbox" @click="lightbox = null">
      <img :src="lightbox" alt="" />
    </div>
  </Teleport>
</template>

<style scoped>
.progress {
  position: fixed;
  top: 0;
  left: 0;
  height: 2px;
  background: var(--accent);
  z-index: 60;
  transition: width 0.1s linear;
}

.article {
  /* 原来 900px 在大屏上两侧空白过多；正文 + 目录共约 1200px 更平衡 */
  max-width: 1200px;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--fg-soft);
  margin-bottom: 24px;
}

.back:hover {
  color: var(--accent);
}

.head {
  margin-bottom: 28px;
}

.head__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  margin-bottom: 14px;
}

.head h1 {
  margin: 0;
  font-size: clamp(28px, 4.4vw, 40px);
  line-height: 1.25;
  letter-spacing: -0.025em;
}

.summary {
  margin: 14px 0 0;
  color: var(--fg-soft);
  font-size: 16px;
}

.cover {
  width: 100%;
  margin-top: 20px;
  border-radius: 14px;
  max-height: 380px;
  object-fit: cover;
}

.meta {
  display: flex;
  gap: 16px;
  margin-top: 18px;
  font-size: 13px;
  flex-wrap: wrap;
}

.meta span {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 64px;
  align-items: start;
}

.toc {
  position: sticky;
  top: 96px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  border-left: 1px solid var(--border);
  padding-left: 14px;
  max-height: 70vh;
  overflow: auto;
}

.toc__title {
  margin: 0 0 6px;
  font-size: 12px;
  color: var(--muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.toc__item {
  border: none;
  background: none;
  padding: 4px 0;
  text-align: left;
  font-size: 13px;
  color: var(--fg-soft);
  cursor: pointer;
  line-height: 1.4;
}

.toc__item:hover {
  color: var(--accent);
}

.toc__item--sub {
  padding-left: 12px;
  font-size: 12px;
}

.foot {
  margin-top: 44px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-link:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.actions {
  display: flex;
  gap: 10px;
  margin-top: 20px;
  flex-wrap: wrap;
}

.action {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg-soft);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.action:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.action--on {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-soft);
}

.updated {
  font-size: 12px;
  margin: 16px 0 0;
}

.neighbours {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-top: 28px;
}

.neighbour {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 14px 16px;
  border: 1px solid var(--border);
  border-radius: 12px;
  transition: border-color 0.15s ease;
}

.neighbour:hover {
  border-color: var(--accent);
}

.neighbour--next {
  text-align: right;
}

.neighbour--empty {
  border: none;
}

.neighbour__label {
  font-size: 12px;
  color: var(--muted);
}

.neighbour__title {
  font-size: 14px;
  color: var(--fg-soft);
}

.comments {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--border);
}

.comments__title {
  margin: 0 0 18px;
  font-size: 20px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.comments__empty {
  font-size: 14px;
}

.form {
  margin-top: 24px;
}

.form__reply {
  font-size: 13px;
  color: var(--accent);
  margin: 0 0 8px;
}

.form__input {
  width: 100%;
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  font-size: 14px;
  font-family: inherit;
  resize: vertical;
  outline: none;
}

.form__input:focus {
  border-color: var(--accent);
}

.form__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  font-size: 12px;
}

.link {
  color: var(--accent);
}

.link-btn {
  border: none;
  background: none;
  color: var(--fg-soft);
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
}

.skeleton {
  height: 420px;
  background: linear-gradient(90deg, var(--bg-soft) 25%, var(--card) 50%, var(--bg-soft) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}

.error {
  color: #e5484d;
}

/* 从通知跳转过来时的高亮 */
:deep(.comment--highlight) {
  animation: comment-flash 3s ease;
  border-radius: 10px;
}

@keyframes comment-flash {
  0%,
  60% {
    background: color-mix(in srgb, var(--accent) 16%, transparent);
    box-shadow: 0 0 0 6px color-mix(in srgb, var(--accent) 12%, transparent);
  }
  100% {
    background: transparent;
    box-shadow: none;
  }
}

.lightbox {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgba(0, 0, 0, 0.82);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  cursor: zoom-out;
}

.lightbox img {
  max-width: 100%;
  max-height: 100%;
  border-radius: 8px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);
}

:deep(.prose-img) {
  cursor: zoom-in;
}

@media (max-width: 860px) {
  .body {
    grid-template-columns: 1fr;
  }

  .toc {
    display: none;
  }

  .neighbours {
    grid-template-columns: 1fr;
  }
}
</style>
