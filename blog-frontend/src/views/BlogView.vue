<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Flame, Search } from 'lucide-vue-next'
import { articleApi, taxonomyApi } from '@/api'
import type { ArticleSummary, Category, PageResult, Tag } from '@/api/types'
import ArticleCard from '@/components/ArticleCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import Pagination from '@/components/Pagination.vue'
import { formatCount } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const page = ref<PageResult<ArticleSummary> | null>(null)
const categories = ref<Category[]>([])
const tags = ref<Tag[]>([])
const popular = ref<ArticleSummary[]>([])
const keyword = ref('')
const loading = ref(false)
const error = ref('')

const currentPage = computed(() => Number(route.query.page ?? 1))
const currentCategory = computed(() => String(route.query.category ?? ''))
const currentSort = computed(() => (route.query.sort === 'popular' ? 'popular' : 'latest'))

async function load() {
  loading.value = true
  error.value = ''
  try {
    page.value = await articleApi.page({
      page: currentPage.value,
      size: 9,
      keyword: String(route.query.q ?? '') || undefined,
      category: currentCategory.value || undefined,
      sort: currentSort.value
    })
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

function updateQuery(patch: Record<string, string | number | undefined>) {
  const query = { ...route.query, ...patch }
  Object.keys(query).forEach((key) => {
    if (query[key] === '' || query[key] === undefined) delete query[key]
  })
  // 筛选条件变化时回到第一页，避免落在空页
  if (!('page' in patch)) query.page = '1'
  router.push({ path: '/blog', query })
}

function submitSearch() {
  updateQuery({ q: keyword.value.trim() || undefined })
}

watch(
  () => route.query,
  () => {
    keyword.value = String(route.query.q ?? '')
    load()
  },
  { immediate: true }
)

onMounted(async () => {
  try {
    const [cats, allTags, hot] = await Promise.all([
      taxonomyApi.categories(),
      taxonomyApi.tags(),
      articleApi.page({ page: 1, size: 5, sort: 'popular' })
    ])
    categories.value = cats.filter((c) => c.articleCount > 0)
    tags.value = allTags.filter((t) => t.articleCount > 0).slice(0, 14)
    popular.value = hot.records
  } catch {
    // 侧栏只是辅助信息，失败不阻塞列表
  }
})
</script>

<template>
  <div class="container">
    <header class="head">
      <h1>博客</h1>
      <p class="muted">共 {{ page?.total ?? 0 }} 篇文章</p>
    </header>

    <div class="toolbar">
      <form class="search" @submit.prevent="submitSearch">
        <Search :size="16" class="search__icon" />
        <input v-model="keyword" class="search__input" type="search" placeholder="搜索标题或摘要…" />
      </form>

      <div class="filters">
        <select
          class="select"
          :value="currentCategory"
          @change="updateQuery({ category: ($event.target as HTMLSelectElement).value })"
        >
          <option value="">全部分类</option>
          <option v-for="category in categories" :key="category.id" :value="category.slug">
            {{ category.name }}（{{ category.articleCount }}）
          </option>
        </select>

        <div class="sort">
          <button :class="{ active: currentSort === 'latest' }" @click="updateQuery({ sort: 'latest' })">
            最新
          </button>
          <button :class="{ active: currentSort === 'popular' }" @click="updateQuery({ sort: 'popular' })">
            最热
          </button>
        </div>
      </div>
    </div>

    <p v-if="error" class="error">加载失败：{{ error }}</p>

    <div class="layout">
      <div class="list">
        <div v-if="loading" class="grid">
          <div v-for="i in 4" :key="i" class="card skeleton" />
        </div>

        <template v-else-if="page && page.records.length">
          <div class="grid">
            <ArticleCard v-for="article in page.records" :key="article.id" :article="article" />
          </div>
          <Pagination :current="page.current" :pages="page.pages" @change="(p) => updateQuery({ page: p })" />
        </template>

        <EmptyState
          v-else
          title="没有匹配的文章"
          description="试试更换关键词或分类，也可以清空筛选条件。"
        />
      </div>

      <aside class="sidebar">
        <section v-if="categories.length" class="card side">
          <p class="side__title">分类</p>
          <RouterLink
            v-for="category in categories"
            :key="category.id"
            :to="`/category/${category.slug}`"
            class="side__row"
          >
            <span>{{ category.name }}</span>
            <span class="side__count">{{ category.articleCount }}</span>
          </RouterLink>
        </section>

        <section v-if="popular.length" class="card side">
          <p class="side__title"><Flame :size="14" /> 热门文章</p>
          <RouterLink
            v-for="article in popular"
            :key="article.id"
            :to="`/article/${article.slug}`"
            class="side__article"
          >
            <span class="side__article-title">{{ article.title }}</span>
            <span class="side__count">{{ formatCount(article.viewCount) }} 次</span>
          </RouterLink>
        </section>

        <section v-if="tags.length" class="card side">
          <p class="side__title">标签</p>
          <div class="side__tags">
            <RouterLink v-for="tag in tags" :key="tag.id" :to="`/tag/${tag.slug}`" class="tag-chip">
              # {{ tag.name }}<span class="side__count">{{ tag.articleCount }}</span>
            </RouterLink>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 20px;
}

.head h1 {
  margin: 0;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.toolbar {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
  margin-bottom: 24px;
}

.search {
  position: relative;
  flex: 1;
  min-width: 220px;
}

.search__icon {
  position: absolute;
  left: 14px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--muted);
}

.search__input {
  width: 100%;
  height: 40px;
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

.filters {
  display: flex;
  gap: 8px;
  align-items: center;
}

.select {
  height: 40px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg);
  padding: 0 14px;
  font-size: 14px;
}

.sort {
  display: inline-flex;
  border: 1px solid var(--border);
  border-radius: 999px;
  overflow: hidden;
}

.sort button {
  border: none;
  background: transparent;
  color: var(--fg-soft);
  padding: 9px 16px;
  font-size: 13px;
  cursor: pointer;
}

.sort button.active {
  background: var(--accent);
  color: #fff;
}

.layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 296px;
  gap: 24px;
  align-items: start;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(292px, 1fr));
  gap: 16px;
}

.sidebar {
  display: flex;
  flex-direction: column;
  gap: 14px;
  position: sticky;
  top: 88px;
}

.side {
  padding: 16px 18px;
}

.side__title {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 0 10px;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--muted);
}

.side__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  color: var(--fg-soft);
}

.side__row:hover {
  color: var(--accent);
}

.side__count {
  font-size: 11px;
  color: var(--muted);
}

.side__article {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 7px 0;
  border-bottom: 1px dashed var(--border);
}

.side__article:last-child {
  border-bottom: none;
}

.side__article-title {
  font-size: 13px;
  line-height: 1.45;
  color: var(--fg-soft);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.side__article:hover .side__article-title {
  color: var(--accent);
}

.side__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.side__tags .tag-chip {
  gap: 4px;
  font-size: 12px;
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

@media (max-width: 1024px) {
  .layout {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
  }
}
</style>
