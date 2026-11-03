<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { ArrowLeft, Calendar, Clock, Eye, Tag } from 'lucide-vue-next'
import { articleApi } from '@/api'
import type { ArticleDetail } from '@/api/types'
import { formatDate, formatCount } from '@/utils/format'

const route = useRoute()

const article = ref<ArticleDetail | null>(null)
const html = ref('')
const headings = ref<{ id: string; text: string; level: number }[]>([])
const loading = ref(true)
const error = ref('')
const progress = ref(0)
const contentRef = ref<HTMLElement | null>(null)

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
    // 必须先结束 loading：模板是 v-else-if="loading" 分支，
    // 若此时仍为 true，正文与 ref 都还没渲染，TOC 会拿到空节点。
    loading.value = false
    await nextTick()
    buildToc()
    window.scrollTo({ top: 0 })
  } catch (e) {
    error.value = (e as Error).message
    loading.value = false
  }
}

function buildToc() {
  const container = contentRef.value
  if (!container) {
    headings.value = []
    return
  }
  // 给标题生成锚点，并收集目录；重复标题用序号保证 id 唯一
  const used = new Set<string>()
  const nodes = Array.from(container.querySelectorAll('h2, h3'))
  headings.value = nodes.map((node, index) => {
    let id = `h-${index}-${node.textContent?.slice(0, 20).replace(/\s+/g, '-') ?? ''}`
    while (used.has(id)) id = `${id}-x`
    used.add(id)
    node.setAttribute('id', id)
    return {
      id,
      text: node.textContent ?? '',
      level: Number(node.tagName.substring(1))
    }
  })
  // 外链统一在新窗口打开并加 noopener，防止 window.opener 劫持
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

onMounted(() => {
  load(String(route.params.slug))
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => window.removeEventListener('scroll', onScroll))

watch(
  () => route.params.slug,
  (slug) => {
    if (slug) load(String(slug))
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
        <p class="muted updated">最后更新：{{ formatDate(article.updateTime) }}</p>
      </footer>
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
  margin-top: 48px;
  padding-top: 20px;
  border-top: 1px solid var(--border);
}

.tags {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.updated {
  font-size: 12px;
  margin: 16px 0 0;
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
