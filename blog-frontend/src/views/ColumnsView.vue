<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft, BookOpen } from 'lucide-vue-next'
import { taxonomyApi } from '@/api'
import type { ArticleSummary, Column } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const columns = ref<Column[]>([])
const current = ref<Column | null>(null)
const articles = ref<ArticleSummary[]>([])
const loading = ref(true)

/**
 * 列表与详情共用同一个组件（/columns 与 /column/:slug），
 * Vue Router 会复用实例不触发 onMounted，所以必须监听路由参数重新加载，
 * 否则站内点击专栏卡片只会改 URL 不换内容。
 */
async function load() {
  loading.value = true
  const slug = route.params.slug ? String(route.params.slug) : ''
  try {
    if (slug) {
      const [column, list] = await Promise.all([
        taxonomyApi.column(slug),
        taxonomyApi.columnArticles(slug)
      ])
      current.value = column
      articles.value = list
    } else {
      current.value = null
      articles.value = []
      columns.value = await taxonomyApi.columns()
    }
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.slug, load)
</script>

<template>
  <div class="container">
    <RouterLink v-if="current" to="/columns" class="back">
      <ArrowLeft :size="15" /> 全部专栏
    </RouterLink>

    <header class="head">
      <h1>{{ current ? current.name : '专栏' }}</h1>
      <p class="muted">{{ current?.description || '成体系的学习路径与系列文章' }}</p>
    </header>

    <div v-if="loading" class="grid">
      <div v-for="i in 3" :key="i" class="card skeleton" />
    </div>

    <template v-else-if="current">
      <div class="summary card">
        <BookOpen :size="18" class="summary__icon" />
        <span>共收录 {{ articles.length }} 篇文章</span>
        <span class="muted">按专栏顺序阅读效果更好</span>
      </div>

      <div v-if="articles.length" class="grid">
        <ArticleCard v-for="article in articles" :key="article.id" :article="article" />
      </div>
      <EmptyState v-else title="专栏还没有文章" description="在后台把文章加入专栏后会显示在这里。" />
    </template>

    <div v-else-if="columns.length" class="grid">
      <RouterLink
        v-for="column in columns"
        :key="column.id"
        :to="`/column/${column.slug}`"
        class="card column"
      >
        <BookOpen :size="18" class="column__icon" />
        <span class="column__name">{{ column.name }}</span>
        <p class="column__desc muted">{{ column.description || '暂无简介' }}</p>
        <span class="column__count">{{ column.articleCount }} 篇文章</span>
      </RouterLink>
    </div>

    <EmptyState v-else title="还没有专栏" description="在后台创建专栏后会显示在这里。" />
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--fg-soft);
  margin-bottom: 14px;
}

.head {
  margin-bottom: 20px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.summary {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 18px;
  margin-bottom: 16px;
  font-size: 14px;
}

.summary__icon {
  color: var(--accent);
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.column {
  padding: 22px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.column__icon {
  color: var(--accent);
  margin-bottom: 4px;
}

.column__name {
  font-weight: 600;
  font-size: 16px;
}

.column__desc {
  margin: 0;
  font-size: 13px;
}

.column__count {
  font-size: 12px;
  color: var(--accent);
  margin-top: 6px;
}

.skeleton {
  height: 150px;
  background: linear-gradient(90deg, var(--bg-soft) 25%, var(--card) 50%, var(--bg-soft) 75%);
  background-size: 200% 100%;
  animation: shimmer 1.4s infinite;
}

@keyframes shimmer {
  to {
    background-position: -200% 0;
  }
}
</style>
