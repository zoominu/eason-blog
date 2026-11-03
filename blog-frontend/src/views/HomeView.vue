<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowRight, BookOpen, Eye, FileText, Sparkles, Tag as TagIcon } from 'lucide-vue-next'
import { articleApi, siteApi, taxonomyApi } from '@/api'
import type {
  ArticleSummary,
  Category,
  Column,
  SiteConfig,
  SiteStatistics,
  Tag
} from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { formatCount } from '@/utils/format'

const config = ref<SiteConfig>({})
const stats = ref<SiteStatistics | null>(null)
const latest = ref<ArticleSummary[]>([])
const recommended = ref<ArticleSummary[]>([])
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const columns = ref<Column[]>([])
const loading = ref(true)
const error = ref('')

onMounted(async () => {
  try {
    // 并行请求，首屏只等待最慢的一个
    const [cfg, st, lt, rc, ct, tg, cl] = await Promise.all([
      siteApi.config(),
      siteApi.statistics(),
      articleApi.latest(6),
      articleApi.recommended(6),
      taxonomyApi.categories(),
      taxonomyApi.tags(),
      taxonomyApi.columns()
    ])
    config.value = cfg
    stats.value = st
    latest.value = lt
    recommended.value = rc.filter((item) => !lt.some((l) => l.id === item.id))
    categories.value = ct
    tags.value = tg.filter((tag) => tag.articleCount > 0).slice(0, 12)
    columns.value = cl
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container">
    <section class="hero fade-in">
      <div class="hero__text">
        <p class="hero__eyebrow">
          <Sparkles :size="14" />
          {{ config['site.subtitle'] || '记录 · 思考 · 分享' }}
        </p>
        <h1 class="hero__title">{{ config['site.title'] || 'Eason Blog' }}</h1>
        <p class="hero__desc">
          {{
            config['site.description'] ||
            '一个关于 Java、Spring Boot、MySQL 与 Vue 的个人技术博客。'
          }}
        </p>
        <div class="hero__actions">
          <RouterLink to="/blog" class="btn btn-primary">
            浏览全部文章 <ArrowRight :size="15" />
          </RouterLink>
          <RouterLink to="/about" class="btn">关于作者</RouterLink>
        </div>
      </div>

      <aside class="hero__panel card">
        <p class="panel__label">站点概览</p>
        <div v-if="stats" class="panel__grid">
          <div class="metric">
            <FileText :size="16" class="metric__icon" />
            <span class="metric__value">{{ formatCount(stats.articleCount) }}</span>
            <span class="metric__label">篇文章</span>
          </div>
          <div class="metric">
            <Eye :size="16" class="metric__icon" />
            <span class="metric__value">{{ formatCount(stats.viewCount) }}</span>
            <span class="metric__label">次阅读</span>
          </div>
          <div class="metric">
            <BookOpen :size="16" class="metric__icon" />
            <span class="metric__value">{{ stats.categoryCount }}</span>
            <span class="metric__label">个分类</span>
          </div>
          <div class="metric">
            <TagIcon :size="16" class="metric__icon" />
            <span class="metric__value">{{ stats.tagCount }}</span>
            <span class="metric__label">个标签</span>
          </div>
        </div>
        <p v-else class="panel__placeholder">统计数据加载中…</p>

        <div v-if="tags.length" class="panel__tags">
          <RouterLink v-for="tag in tags.slice(0, 6)" :key="tag.id" :to="`/tag/${tag.slug}`" class="tag-chip">
            # {{ tag.name }}
          </RouterLink>
        </div>
      </aside>
    </section>

    <p v-if="error" class="error">加载失败：{{ error }}（请确认后端已启动）</p>

    <section v-if="!error" class="section">
      <header class="section__head">
        <div>
          <h2>最新文章</h2>
          <p class="section__desc">最近发布的笔记与实践记录</p>
        </div>
        <RouterLink to="/blog" class="section__more">全部文章 <ArrowRight :size="15" /></RouterLink>
      </header>

      <div v-if="loading" class="grid">
        <div v-for="i in 3" :key="i" class="card skeleton" />
      </div>
      <div v-else-if="latest.length" class="grid">
        <ArticleCard v-for="article in latest" :key="article.id" :article="article" />
      </div>
      <EmptyState v-else />
    </section>

    <section v-if="!error && recommended.length" class="section">
      <header class="section__head">
        <div>
          <h2>推荐阅读</h2>
          <p class="section__desc">精选内容，适合系统学习</p>
        </div>
      </header>
      <div class="grid">
        <ArticleCard v-for="article in recommended" :key="article.id" :article="article" />
      </div>
    </section>

    <section v-if="!error && (categories.length || columns.length)" class="section">
      <header class="section__head">
        <div>
          <h2>按主题浏览</h2>
          <p class="section__desc">分类、专栏与标签</p>
        </div>
      </header>

      <div class="topics">
        <div v-if="categories.length" class="topics__block">
          <p class="block__label">分类</p>
          <div class="cats">
            <RouterLink
              v-for="category in categories"
              :key="category.id"
              :to="`/category/${category.slug}`"
              class="cat card"
            >
              <span class="cat__name">{{ category.name }}</span>
              <span class="cat__count">{{ category.articleCount }} 篇</span>
            </RouterLink>
          </div>
        </div>

        <div v-if="columns.length" class="topics__block">
          <p class="block__label">专栏</p>
          <div class="columns">
            <RouterLink
              v-for="column in columns"
              :key="column.id"
              :to="`/column/${column.slug}`"
              class="column card"
            >
              <span class="column__name">{{ column.name }}</span>
              <span class="column__desc">{{ column.description || '系列文章' }}</span>
              <span class="column__count">{{ column.articleCount }} 篇</span>
            </RouterLink>
          </div>
        </div>
      </div>

      <div v-if="tags.length" class="tag-cloud">
        <RouterLink v-for="tag in tags" :key="tag.id" :to="`/tag/${tag.slug}`" class="tag-chip tag">
          # {{ tag.name }}
          <span class="tag__count">{{ tag.articleCount }}</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>

<style scoped>
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(280px, 1fr);
  gap: 32px;
  align-items: center;
  padding: 24px 0 44px;
}

.hero__eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--accent);
  background: var(--accent-soft);
  padding: 4px 12px;
  border-radius: 999px;
}

.hero__title {
  margin: 0;
  font-size: clamp(36px, 5vw, 56px);
  letter-spacing: -0.03em;
  line-height: 1.08;
  font-weight: 700;
}

.hero__desc {
  max-width: 560px;
  margin: 16px 0 0;
  color: var(--fg-soft);
  font-size: 16px;
}

.hero__actions {
  display: flex;
  gap: 10px;
  margin-top: 26px;
  flex-wrap: wrap;
}

.hero__panel {
  padding: 20px 22px;
  align-self: stretch;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.panel__label {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.panel__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.metric {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 12px;
  border-radius: 12px;
  background: var(--bg-soft);
}

.metric__icon {
  color: var(--accent);
  margin-bottom: 2px;
}

.metric__value {
  font-size: 20px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.metric__label {
  font-size: 12px;
  color: var(--muted);
}

.panel__placeholder {
  margin: 0;
  font-size: 13px;
  color: var(--muted);
}

.panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;
}

.section {
  padding: 26px 0;
}

.section__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.section__head h2 {
  margin: 0;
  font-size: 20px;
  letter-spacing: -0.01em;
}

.section__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--muted);
}

.section__more {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--fg-soft);
  white-space: nowrap;
}

.section__more:hover {
  color: var(--accent);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.topics {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.block__label {
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.cats {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 10px;
}

.cat {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.cat:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.cat__name {
  font-weight: 500;
  font-size: 14px;
}

.cat__count {
  font-size: 12px;
  color: var(--muted);
}

.columns {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.column {
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 2px;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.column:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.column__name {
  font-weight: 600;
  font-size: 14px;
}

.column__desc {
  font-size: 12px;
  color: var(--muted);
}

.column__count {
  font-size: 12px;
  color: var(--accent);
  margin-top: 4px;
}

.tag-cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 22px;
}

.tag {
  padding: 6px 12px;
  gap: 5px;
  transition: border-color 0.15s ease, color 0.15s ease;
}

.tag:hover {
  border-color: var(--accent);
  color: var(--accent);
}

.tag__count {
  font-size: 11px;
  color: var(--muted);
}

.skeleton {
  height: 172px;
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
  font-size: 14px;
}

@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .topics {
    grid-template-columns: 1fr;
  }
}
</style>
