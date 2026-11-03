<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter, type RouteLocationRaw } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  ArrowRight,
  ChatDotRound,
  CollectionTag,
  Document,
  EditPen,
  FolderOpened,
  Timer,
  User
} from '@element-plus/icons-vue'
import { dashboardApi } from '@/api'
import type { DashboardData } from '@/api/types'
import { formatDateTime, formatUptime } from '@/utils/format'

const router = useRouter()

const data = ref<DashboardData | null>(null)
const loading = ref(true)
const error = ref('')

const STATUS_TEXT: Record<number, string> = {
  0: '草稿',
  1: '已发布',
  2: '已下线',
  3: '回收站'
}

/** 指标卡：value + 标题 + 点击跳转目标，把仪表盘变成导航入口 */
const cards = computed<
  { label: string; value: number; hint: string; to: RouteLocationRaw; icon: unknown; tone: string }[]
>(() => {
  if (!data.value) return []
  return [
    {
      label: '已发布文章',
      value: data.value.publishedArticles,
      hint: '查看全部已发布',
      to: { path: '/articles', query: { status: 1 } },
      icon: Document,
      tone: 'primary'
    },
    {
      label: '草稿',
      value: data.value.draftArticles,
      hint: '继续未完成的写作',
      to: { path: '/articles', query: { status: 0 } },
      icon: EditPen,
      tone: 'warning'
    },
    {
      label: '累计阅读',
      value: data.value.totalViews,
      hint: '按浏览量排序',
      to: { path: '/articles' },
      icon: Timer,
      tone: 'info'
    },
    {
      label: '注册用户',
      value: data.value.users,
      hint: '用户与权限管理',
      to: { path: '/users' },
      icon: User,
      tone: 'success'
    },
    {
      label: '分类',
      value: data.value.categories,
      hint: '维护分类树',
      to: { path: '/categories' },
      icon: FolderOpened,
      tone: 'info'
    },
    {
      label: '标签',
      value: data.value.tags,
      hint: '维护标签',
      to: { path: '/tags' },
      icon: CollectionTag,
      tone: 'info'
    },
    {
      label: '已下线',
      value: data.value.offlineArticles,
      hint: '重新发布或回收',
      to: { path: '/articles', query: { status: 2 } },
      icon: ChatDotRound,
      tone: 'warning'
    },
    {
      label: '回收站',
      value: data.value.recycledArticles,
      hint: '可恢复的文章',
      to: { path: '/articles', query: { status: 3 } },
      icon: FolderOpened,
      tone: 'danger'
    }
  ]
})

function go(to: RouteLocationRaw) {
  router.push(to)
}

function openArticle(id: number) {
  router.push(`/articles/${id}/edit`)
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
      <div>
        <h2 class="page__title">仪表盘</h2>
        <p class="page__desc">内容、用户与运行时概况 · 点击卡片可直接进入对应模块</p>
      </div>
      <el-button type="primary" @click="router.push('/articles/create')">写文章</el-button>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <template v-else-if="data">
      <section class="cards">
        <button
          v-for="card in cards"
          :key="card.label"
          class="stat-card stat-card--link"
          :class="`stat-card--${card.tone}`"
          @click="go(card.to)"
        >
          <div class="stat-card__top">
            <el-icon :size="18"><component :is="card.icon" /></el-icon>
            <el-icon class="stat-card__arrow" :size="14"><ArrowRight /></el-icon>
          </div>
          <div class="stat-card__value">{{ card.value }}</div>
          <div class="stat-card__label">{{ card.label }}</div>
          <div class="stat-card__hint">{{ card.hint }}</div>
        </button>
      </section>

      <div class="grid">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel__head">
              <span class="panel__title">最近登录用户</span>
              <el-button link type="primary" @click="router.push('/users')">用户管理</el-button>
            </div>
          </template>
          <el-table :data="data.recentUsers" size="small" empty-text="还没有登录记录">
            <el-table-column label="用户" min-width="160">
              <template #default="{ row }">
                <div class="user-cell">
                  <span class="user-cell__name">{{ row.nickname }}</span>
                  <span class="user-cell__email">{{ row.email }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="角色" width="110">
              <template #default="{ row }">
                <el-tag v-for="role in row.roles" :key="role" size="small" :type="role === 'SUPER_ADMIN' ? 'danger' : 'info'">
                  {{ role === 'SUPER_ADMIN' ? '管理员' : '用户' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最后登录" width="160">
              <template #default="{ row }">{{ formatDateTime(row.lastLoginTime) }}</template>
            </el-table-column>
            <el-table-column label="IP" width="130">
              <template #default="{ row }">
                <span class="mono">{{ row.lastLoginIp || '—' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel__head">
              <span class="panel__title">最近登录记录</span>
              <el-button link type="primary" @click="router.push('/logs')">全部日志</el-button>
            </div>
          </template>
          <el-table :data="data.recentLogins" size="small" empty-text="暂无登录日志">
            <el-table-column label="时间" width="150">
              <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column prop="email" label="账号" min-width="150" show-overflow-tooltip />
            <el-table-column label="IP" width="120">
              <template #default="{ row }">
                <span class="mono">{{ row.ip || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.loginStatus === 1 ? 'success' : 'danger'">
                  {{ row.loginStatus === 1 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </div>

      <div class="grid">
        <el-card class="panel" shadow="never">
          <template #header>
            <div class="panel__head">
              <span class="panel__title">最近更新</span>
              <el-button link type="primary" @click="router.push('/articles')">全部文章</el-button>
            </div>
          </template>
          <el-table :data="data.recentArticles" size="small" @row-click="(row: any) => openArticle(row.id)">
            <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'success' : row.status === 3 ? 'danger' : 'info'">
                  {{ STATUS_TEXT[row.status] ?? row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="更新时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.updateTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="panel" shadow="never">
          <template #header><span class="panel__title">系统信息</span></template>
          <el-descriptions :column="1" size="small" border>
            <el-descriptions-item label="应用">{{ data.system.appName }}</el-descriptions-item>
            <el-descriptions-item label="环境">{{ data.system.profile }}</el-descriptions-item>
            <el-descriptions-item label="Java">{{ data.system.javaVersion }}</el-descriptions-item>
            <el-descriptions-item label="操作系统">
              {{ data.system.osName }} / {{ data.system.osArch }}
            </el-descriptions-item>
            <el-descriptions-item label="启动时间">
              {{ formatDateTime(data.system.startedAt) }}
            </el-descriptions-item>
            <el-descriptions-item label="运行时长">
              {{ formatUptime(data.system.uptimeSeconds) }}
            </el-descriptions-item>
            <el-descriptions-item label="内存">
              {{ data.system.heapUsedMb }} MB / {{ data.system.heapMaxMb }} MB
            </el-descriptions-item>
            <el-descriptions-item label="数据库">
              {{ data.system.dbProduct }} {{ data.system.dbVersion }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>
      </div>

      <el-card class="panel" shadow="never">
        <template #header><span class="panel__title">归档</span></template>
        <div v-if="data.archive.length" class="archive">
          <span v-for="item in data.archive" :key="item.month" class="archive__item">
            {{ item.month }} · {{ item.total }} 篇
          </span>
        </div>
        <p v-else class="muted">暂无归档数据</p>
      </el-card>
    </template>

    <el-empty v-else :description="error || '加载失败'" />
  </div>
</template>

<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

@media (max-width: 980px) {
  .cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.stat-card {
  text-align: left;
  background: var(--admin-card);
  border: 1px solid var(--admin-border);
  border-radius: 12px;
  padding: 14px 16px;
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease, box-shadow 0.15s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  border-color: var(--el-color-primary);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.06);
}

.stat-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  color: var(--admin-muted);
}

.stat-card__arrow {
  opacity: 0;
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.stat-card:hover .stat-card__arrow {
  opacity: 1;
  transform: translateX(2px);
}

.stat-card__value {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  margin-top: 6px;
  color: var(--admin-text);
}

.stat-card__label {
  font-size: 13px;
  color: var(--admin-text);
}

.stat-card__hint {
  font-size: 12px;
  color: var(--admin-muted);
  margin-top: 2px;
}

.stat-card--primary .stat-card__top {
  color: var(--el-color-primary);
}
.stat-card--warning .stat-card__top {
  color: var(--el-color-warning);
}
.stat-card--success .stat-card__top {
  color: var(--el-color-success);
}
.stat-card--danger .stat-card__top {
  color: var(--el-color-danger);
}

.grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}

.panel {
  border-radius: 12px;
}

.panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel__title {
  font-size: 14px;
  font-weight: 500;
}

.user-cell {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.user-cell__name {
  font-size: 13px;
}

.user-cell__email {
  font-size: 12px;
  color: var(--admin-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 220px;
}

.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
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

@media (max-width: 1100px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
