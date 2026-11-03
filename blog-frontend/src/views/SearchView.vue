<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from 'lucide-vue-next'
import { searchApi } from '@/api/search'
import type { ArticleSummary, PageResult } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'

const route = useRoute()
const router = useRouter()

const keyword = ref('')
const result = ref<PageResult<ArticleSummary> | null>(null)
const loading = ref(false)
const error = ref('')

async function load() {
  const q = String(route.query.q ?? '').trim()
  keyword.value = q
  if (!q) {
    result.value = null
    return
  }
  loading.value = true
  error.value = ''
  try {
    result.value = await searchApi.query({
      keyword: q,
      page: Number(route.query.page ?? 1),
      size: 10
    })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function submit() {
  const q = keyword.value.trim()
  router.push({ path: '/search', query: q ? { q } : {} })
}

function goPage(page: number) {
  router.push({ path: '/search', query: { q: String(route.query.q ?? ''), page } })
}

onMounted(load)
watch(() => route.query, load)
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>搜索</h1>
      <p class="muted">在标题、摘要与正文中查找</p>
    </header>

    <form class="search" @submit.prevent="submit">
      <Search :size="16" class="search__icon" />
      <input v-model="keyword" class="search__input" type="search" placeholder="输入关键词，回车搜索…" />
      <button class="btn btn-primary" type="submit">搜索</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <template v-if="result">
      <p class="summary muted">
        「{{ route.query.q }}」共找到 {{ result.total }} 篇
      </p>

      <div v-if="loading" class="grid">
        <div v-for="i in 3" :key="i" class="card skeleton" />
      </div>
      <div v-else-if="result.records.length" class="grid">
        <ArticleCard v-for="article in result.records" :key="article.id" :article="article" />
      </div>
      <EmptyState v-else title="没有找到相关文章" description="换个关键词试试，或浏览分类与标签。" />

      <Pagination
        v-if="result.pages > 1"
        :current="result.current"
        :pages="result.pages"
        @change="goPage"
      />
    </template>

    <p v-else-if="!loading" class="hint muted">输入关键词开始搜索。</p>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 980px;
}

.head {
  margin-bottom: 18px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.search {
  position: relative;
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
}

.search__icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--muted);
}

.search__input {
  flex: 1;
  height: 42px;
  padding: 0 16px 0 38px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  font-size: 14px;
  outline: none;
}

.search__input:focus {
  border-color: var(--accent);
}

.summary {
  font-size: 13px;
  margin-bottom: 14px;
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
  font-size: 14px;
}

.hint {
  font-size: 14px;
}
</style>
