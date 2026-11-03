<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { dashboardApi } from '@/api'
import type { DashboardData } from '@/api/types'

const data = ref<DashboardData | null>(null)
const loading = ref(true)
const error = ref('')

const STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已下线',
  3: '回收站'
}

onMounted(async () => {
  try {
    data.value = await dashboardApi.overview()
  } catch (e) {
    error.value = (e as Error).message
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <h2 class="page__title">仪表盘</h2>
      <p class="page__desc">站点内容与访问概况</p>
    </header>

    <el-skeleton v-if="loading" :rows="4" animated />

    <template v-else-if="data">
      <div class="grid-cards">
        <div class="stat-card">
          <div class="stat-card__label">已发布文章</div>
          <div class="stat-card__value">{{ data.publishedArticles }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__label">草稿</div>
          <div class="stat-card__value">{{ data.draftArticles }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__label">累计阅读</div>
          <div class="stat-card__value">{{ data.totalViews }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__label">注册用户</div>
          <div class="stat-card__value">{{ data.users }}</div>
        </div>
        <div class="stat-card">
          <div class="stat-card__label">分类 / 标签</div>
          <div class="stat-card__value">{{ data.categories }} / {{ data.tags }}</div>
        </div>
      </div>

      <el-card class="panel" shadow="never">
        <template #header>
          <span class="panel__title">最近更新</span>
        </template>
        <el-table :data="data.recentArticles" size="small">
          <el-table-column prop="title" label="标题" min-width="240" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ STATUS_TEXT[row.status] ?? row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" width="180" />
        </el-table>
      </el-card>

      <el-card class="panel" shadow="never">
        <template #header>
          <span class="panel__title">归档</span>
        </template>
        <div v-if="data.archive.length" class="archive">
          <span v-for="item in data.archive" :key="item.month" class="archive__item">
            {{ item.month }} · {{ item.total }} 篇
          </span>
        </div>
        <p v-else class="muted">暂无归档数据</p>
      </el-card>
    </template>

    <el-empty v-else description="加载失败" />
  </div>
</template>

<style scoped>
.panel {
  margin-top: 16px;
  border-radius: 12px;
}

.panel__title {
  font-size: 14px;
  font-weight: 500;
}

.archive {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.archive__item {
  font-size: 13px;
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--admin-border);
  color: var(--admin-muted);
}

.muted {
  color: var(--admin-muted);
  font-size: 13px;
}
</style>
