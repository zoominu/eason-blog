<script setup lang="ts">
import { onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { articleApi, categoryApi } from '@/api'
import type { AdminArticleListItem, ArticleStatus, Category } from '@/api/types'

const route = useRoute()
const router = useRouter()

const list = ref<AdminArticleListItem[]>([])
const total = ref(0)
const loading = ref(false)
const categories = ref<Category[]>([])

const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  status: undefined as number | undefined,
  categoryId: undefined as number | undefined
})

const STATUS_META: Record<ArticleStatus, { text: string; type: 'success' | 'info' | 'warning' | 'danger' }> = {
  0: { text: '草稿', type: 'info' },
  1: { text: '已发布', type: 'success' },
  2: { text: '已下线', type: 'warning' },
  3: { text: '回收站', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const page = await articleApi.page({
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status,
      categoryId: query.categoryId
    })
    list.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function changeStatus(row: AdminArticleListItem, action: 'publish' | 'offline' | 'delete') {
  const label = { publish: '发布', offline: '下线', delete: '删除' }[action]
  if (action === 'delete') {
    await ElMessageBox.confirm(
      `确定删除《${row.title}》？删除后不可恢复。如果只是不想让访客看到，请用「下线」。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', confirmButtonClass: 'el-button--danger' }
    )
  }
  try {
    if (action === 'publish') await articleApi.publish(row.id)
    if (action === 'offline') await articleApi.offline(row.id)
    if (action === 'delete') await articleApi.remove(row.id)
    ElMessage.success(`${label}成功`)
    load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

/** 从仪表盘等入口带过来的筛选条件：/articles?status=1&categoryId=2 */
function applyRouteQuery() {
  const status = route.query.status
  const categoryId = route.query.categoryId
  query.status = status !== undefined && status !== '' ? Number(status) : undefined
  query.categoryId = categoryId !== undefined && categoryId !== '' ? Number(categoryId) : undefined
  query.page = 1
}

onMounted(async () => {
  applyRouteQuery()
  load()
  try {
    categories.value = await categoryApi.list()
  } catch {
    // 分类筛选是辅助功能，失败不影响列表
  }
})

// 已在文章页时再从仪表盘点进来，也要跟随 query 变化
watch(
  () => route.query,
  () => {
    if (route.path !== '/articles') return
    applyRouteQuery()
    load()
  }
)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">文章管理</h2>
        <p class="page__desc">共 {{ total }} 篇 · 草稿与已下线的文章不会出现在前台，删除后不可恢复</p>
      </div>
      <el-button type="primary" @click="router.push('/articles/create')">新建文章</el-button>
    </header>

    <el-card shadow="never" class="panel">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="搜索标题或摘要"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="search">
          <el-option label="草稿" :value="0" />
          <el-option label="已发布" :value="1" />
          <el-option label="已下线" :value="2" />
        </el-select>
        <el-select v-model="query.categoryId" placeholder="全部分类" clearable style="width: 160px" @change="search">
          <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" size="default">
        <el-table-column label="标题" min-width="260">
          <template #default="{ row }">
            <div class="title-cell">
              <span class="title-cell__text">{{ row.title }}</span>
              <span class="title-cell__slug">/article/{{ row.slug }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="分类" width="120">
          <template #default="{ row }">
            <span v-if="row.categoryName">{{ row.categoryName }}</span>
            <span v-else class="muted">未分类</span>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="180">
          <template #default="{ row }">
            <el-tag v-for="tag in row.tags" :key="tag" size="small" type="info" class="tag">{{ tag }}</el-tag>
            <span v-if="!row.tags.length" class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_META[row.status as ArticleStatus].type">
              {{ STATUS_META[row.status as ArticleStatus].text }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="浏览" width="80">
          <template #default="{ row }">{{ row.viewCount }}</template>
        </el-table-column>
        <el-table-column label="更新时间" width="170">
          <template #default="{ row }">{{ row.updateTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="router.push(`/articles/${row.id}/edit`)">编辑</el-button>
            <el-button
              v-if="row.status !== 1"
              link
              type="success"
              @click="changeStatus(row, 'publish')"
            >
              发布
            </el-button>
            <el-button v-if="row.status === 1" link type="warning" @click="changeStatus(row, 'offline')">
              下线
            </el-button>
            <el-button link type="danger" @click="changeStatus(row, 'delete')">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, sizes, prev, pager, next"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          @current-change="load"
          @size-change="() => { query.page = 1; load() }"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}

.filters {
  display: flex;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.title-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.title-cell__text {
  font-weight: 500;
}

.title-cell__slug {
  font-size: 12px;
  color: var(--admin-muted);
}

.tag {
  margin-right: 4px;
}

.muted {
  color: var(--admin-muted);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
