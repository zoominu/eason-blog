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
import { ArrowLeft, Bookmark, Calendar, Clock, Eye, Heart, MessageSquare, Send, Tag } from 'lucide-vue-next'
import { articleApi, interactionApi } from '@/api'
import type { ArticleDetail, CommentItem, InteractionState } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { formatDate, formatCount } from '@/utils/format'

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
const commentText = ref('')
const replyTo = ref<CommentItem | null>(null)
const submitting = ref(false)
const commentHint = ref('')

marked.setOptions({ gfm: true, breaks: false })

const readingLabel = computed(() => (article.value ? `${article.value.readingMinutes} 分钟` : ''))

async function load(slug: string) {
  loading.value = true
  error.value = ''
  headings.value = []
  try {
    const data = await articleApi.detail(slug)
    article.value = data
    // 先净化再插入 DOM：Markdown 可能包含原始 HTML，直接 v-html 等于开放 XSS
    html.value = DOMPurify.sanitize(marked.parse(data.content) as string, {
      USE_PROFILES: { html: true },
      ADD_ATTR: ['target']
    })
    loading.value = false
    await nextTick()
    buildToc()
    highlightCode()
    applySeo(data)
    window.scrollTo({ top: 0 })

    interaction.value = await interactionApi.status(data.id)
    comments.value = await interactionApi.comments(slug)
  } catch (e) {
    error.value = (e as Error).message
    loading.value = false
  }
}

/** 代码高亮：只处理带语言标记的 ``` 代码块，其余保持纯文本 */
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
      // 语言未注册时忽略，不影响阅读
    }
  })
}

/** 单页应用的 SEO：动态写入 title / description / OpenGraph */
function applySeo(data: ArticleDetail) {
  const title = data.seoTitle || data.title
  document.title = `${title} · Eason Blog`

  const setMeta = (selector: string, attr: string, value: string) => {
    let el = document.head.querySelector(selector) as HTMLMetaElement | null
    if (!el) {
      el = document.createElement('meta')
      const [key, val] = selector.replace(/[[\]']/g, '').split('=')
      el.setAttribute(key, val)
      document.head.appendChild(el)
    }
    el.setAttribute(attr, value)
  }

  const description = data.seoDescription || data.summary || ''
  if (description) setMeta('meta[name="description"]', 'content', description)
  setMeta('meta[property="og:title"]', 'content', title)
  if (description) setMeta('meta[property="og:description"]', 'content', description)
  setMeta('meta[property="og:type"]', 'content', 'article')
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
  interaction.value = interaction.value.liked
    ? await interactionApi.unlike(article.value.id)
    : await interactionApi.like(article.value.id)
  if (article.value) article.value.likeCount = interaction.value.likeCount
}

async function toggleFavorite() {
  if (!requireLogin() || !article.value) return
  interaction.value = interaction.value.favorited
    ? await interactionApi.unfavorite(article.value.id)
    : await interactionApi.favorite(article.value.id)
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

onMounted(() => {
  load(String(route.params.slug))
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => window.removeEventListener('scroll', onScroll))

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
          <span v-if="article.categoryName" class="tag-chip">{{ article.categoryName }}</span>
          <span class="muted">{{ formatDate(article.publishTime) }}</span>
        </div>
        <h1>{{ article.title }}</h1>
        <p class="summary">{{ article.summary }}</p>
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
          <Tag :size="14" class="muted" />
          <span v-for="tag in article.tags" :key="tag" class="tag-chip">{{ tag }}</span>
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
            <MessageSquare :size="16" /> 评论 {{ interaction.commentCount }}
          </a>
        </div>

        <p class="muted updated">最后更新：{{ formatDate(article.updateTime) }}</p>
      </footer>

      <section id="comments" class="comments">
        <h2 class="comments__title">评论 {{ comments.length ? `(${comments.length})` : '' }}</h2>

        <div v-if="comments.length" class="comment-list">
          <div v-for="comment in comments" :key="comment.id" class="comment">
            <div class="comment__head">
              <span class="comment__name">{{ comment.nickname }}</span>
              <span class="muted comment__time">{{ formatDate(comment.createTime) }}</span>
            </div>
            <p class="comment__content">{{ comment.content }}</p>
            <button class="comment__reply" @click="startReply(comment)">回复</button>

            <div v-if="comment.replies.length" class="replies">
              <div v-for="reply in comment.replies" :key="reply.id" class="comment comment--reply">
                <div class="comment__head">
                  <span class="comment__name">{{ reply.nickname }}</span>
                  <span class="muted comment__time">{{ formatDate(reply.createTime) }}</span>
                </div>
                <p class="comment__content">{{ reply.content }}</p>
                <button class="comment__reply" @click="startReply(reply)">回复</button>
              </div>
            </div>
          </div>
        </div>
        <p v-else class="muted comments__empty">还没有评论，来说点什么吧。</p>

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
  max-width: 900px;
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
  grid-template-columns: minmax(0, 1fr) 200px;
  gap: 40px;
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
  gap: 18px;
}

.comment {
  padding-bottom: 14px;
  border-bottom: 1px dashed var(--border);
}

.comment__head {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-bottom: 6px;
}

.comment__name {
  font-weight: 600;
  font-size: 14px;
}

.comment__time {
  font-size: 12px;
}

.comment__content {
  margin: 0;
  font-size: 14px;
  color: var(--fg-soft);
  line-height: 1.7;
  white-space: pre-wrap;
}

.comment__reply {
  margin-top: 6px;
  border: none;
  background: none;
  padding: 0;
  font-size: 12px;
  color: var(--accent);
  cursor: pointer;
}

.replies {
  margin-top: 12px;
  padding-left: 16px;
  border-left: 2px solid var(--border);
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.comment--reply {
  border-bottom: none;
  padding-bottom: 0;
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

@media (max-width: 860px) {
  .body {
    grid-template-columns: 1fr;
  }

  .toc {
    display: none;
  }
}
</style>
