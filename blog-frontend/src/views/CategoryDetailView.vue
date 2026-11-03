<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ArrowLeft } from 'lucide-vue-next'
import { taxonomyApi } from '@/api'
import type { ArticleSummary, PageResult } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const page = ref<PageResult<ArticleSummary> | null>(null)
const loading = ref(true)
const error = ref('')

async function load(slug: string, pageNo = 1) {
  loading.value = true
  error.value = ''
  try {
    page.value = await taxonomyApi.categoryArticles(slug, pageNo, 10)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(() => load(String(route.params.slug)))

watch(
  () => route.params.slug,
  (slug) => slug && load(String(slug))
)
</script>

<template>
  <div class="container">
    <RouterLink to="/categories" class="back"><ArrowLeft :size="15" /> 全部分类</RouterLink>
    <header class="head">
      <h1>{{ page?.records[0]?.categoryName || '分类文章' }}</h1>
      <p class="muted">共 {{ page?.total ?? 0 }} 篇</p>
    </header>

    <p v-if="error" class="error">{{ error }}</p>
    <div v-else-if="loading" class="grid">
      <div v-for="i in 4" :key="i" class="card skeleton" />
    </div>
    <template v-else-if="page && page.records.length">
      <div class="grid">
        <ArticleCard v-for="article in page.records" :key="article.id" :article="article" />
      </div>
      <Pagination :current="page.current" :pages="page.pages" @change="(p) => load(String(route.params.slug), p)" />
    </template>
    <EmptyState v-else title="该分类下暂无文章" />
  </div>
</template>

<style scoped>
.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--fg-soft);
  margin-bottom: 16px;
}

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
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.skeleton {
  height: 168px;
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
</style>
