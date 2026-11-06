<!--
  标签总览：展示标签及公开文章数，点击进入标签详情。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { taxonomyApi } from '@/api'
import type { Tag } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'

const tags = ref<Tag[]>([])
const loading = ref(true)

onMounted(async () => {
  try {
    const all = await taxonomyApi.tags()
    // 只展示真正有文章的标签，避免出现一堆 0 篇的死链
    tags.value = all.filter((tag) => tag.articleCount > 0)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="container">
    <header class="head">
      <h1>标签</h1>
      <p class="muted">共 {{ tags.length }} 个标签</p>
    </header>

    <div v-if="loading" class="cloud">
      <span v-for="i in 8" :key="i" class="tag-chip skeleton-chip" />
    </div>
    <div v-else-if="tags.length" class="cloud">
      <RouterLink v-for="tag in tags" :key="tag.id" :to="`/tag/${tag.slug}`" class="tag-chip tag">
        # {{ tag.name }}
        <span class="muted">{{ tag.articleCount }}</span>
      </RouterLink>
    </div>
    <EmptyState v-else title="还没有标签" description="发布文章时添加标签后会显示在这里。" />
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

.cloud {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.tag {
  padding: 8px 14px;
  font-size: 13px;
  gap: 6px;
  transition: transform 0.15s ease, border-color 0.15s ease, color 0.15s ease;
}

.tag:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
  color: var(--accent);
}

.skeleton-chip {
  width: 92px;
  height: 34px;
  background: var(--bg-soft);
  animation: pulse 1.4s infinite;
}

@keyframes pulse {
  50% {
    opacity: 0.5;
  }
}
</style>
