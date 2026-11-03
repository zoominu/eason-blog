<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { Layers } from 'lucide-vue-next'
import { taxonomyApi } from '@/api'
import type { Column } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const route = useRoute()
const columns = ref<Column[]>([])
const current = ref<Column | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    const slug = route.params.slug ? String(route.params.slug) : ''
    if (slug) {
      current.value = await taxonomyApi.column(slug)
    } else {
      columns.value = await taxonomyApi.columns()
    }
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container">
    <header class="head">
      <h1>{{ current ? current.name : '专栏' }}</h1>
      <p class="muted">{{ current?.description || '成体系的学习路径与系列文章' }}</p>
    </header>

    <div v-if="loading" class="grid">
      <div v-for="i in 3" :key="i" class="card skeleton" />
    </div>

    <div v-else-if="current" class="card detail">
      <p class="detail__desc">{{ current.description || '这个专栏还没有简介。' }}</p>
      <p class="muted">共收录 {{ current.articleCount }} 篇文章</p>
      <p class="muted hint">专栏文章列表将在 P3 阶段接入（需要 blog_column_article 关联查询）。</p>
    </div>

    <div v-else-if="columns.length" class="grid">
      <RouterLink
        v-for="column in columns"
        :key="column.id"
        :to="`/column/${column.slug}`"
        class="card column"
      >
        <Layers :size="18" class="column__icon" />
        <span class="column__name">{{ column.name }}</span>
        <p class="column__desc muted">{{ column.description || '暂无简介' }}</p>
        <span class="muted column__count">{{ column.articleCount }} 篇文章</span>
      </RouterLink>
    </div>

    <EmptyState v-else title="还没有专栏" description="在后台创建专栏后会显示在这里。" />
  </div>
</template>

<style scoped>
.head {
  margin-bottom: 24px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
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
  margin-top: 8px;
}

.detail {
  padding: 28px;
}

.detail__desc {
  margin: 0 0 12px;
  color: var(--fg-soft);
}

.hint {
  font-size: 12px;
  margin-top: 16px;
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
