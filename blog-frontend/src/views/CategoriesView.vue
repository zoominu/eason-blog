<!--
  分类总览：展示分类及公开文章数，点击进入分类详情。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { taxonomyApi } from '@/api'
import type { Category } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const categories = ref<Category[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const all = await taxonomyApi.categories()
    // 有文章的分类排在前面，空分类保留（后台可能刚建好还没发文章）
    categories.value = [...all].sort(
      (a, b) => b.articleCount - a.articleCount || a.name.localeCompare(b.name)
    )
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container">
    <header class="head">
      <h1>分类</h1>
      <p class="muted">按主题浏览文章</p>
    </header>

    <div v-if="loading" class="grid">
      <div v-for="i in 4" :key="i" class="card skeleton" />
    </div>
    <div v-else-if="categories.length" class="grid">
      <RouterLink
        v-for="category in categories"
        :key="category.id"
        :to="`/category/${category.slug}`"
        class="card cat"
      >
        <div class="cat__top">
          <span class="cat__name">{{ category.name }}</span>
          <span class="muted">{{ category.articleCount }} 篇</span>
        </div>
        <p class="cat__desc muted">{{ category.description || '暂无描述' }}</p>
      </RouterLink>
    </div>
    <EmptyState v-else title="还没有分类" description="在后台创建分类后会显示在这里。" />
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
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.cat {
  padding: 20px;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.cat:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.cat__top {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  font-size: 13px;
}

.cat__name {
  font-size: 17px;
  font-weight: 600;
}

.cat__desc {
  margin: 8px 0 0;
  font-size: 13px;
}

.skeleton {
  height: 108px;
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
