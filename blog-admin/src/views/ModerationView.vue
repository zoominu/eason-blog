<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { moderationApi, type AdminComment, type AdminGuestbook } from '@/api'

const route = useRoute()

const kind = computed<'comments' | 'guestbook'>(() =>
  route.path.includes('guestbook') ? 'guestbook' : 'comments'
)

const list = ref<(AdminComment | AdminGuestbook)[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({
  page: 1,
  size: 10,
  status: 0 as number | undefined
})

const STATUS_META: Record<number, { text: string; type: 'info' | 'success' | 'danger' }> = {
  0: { text: '待审核', type: 'info' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已拒绝', type: 'danger' }
}

async function load() {
  loading.value = true
  try {
    const page =
      kind.value === 'guestbook'
        ? await moderationApi.guestbook({ ...query })
        : await moderationApi.comments({ ...query })
    list.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function moderate(row: AdminComment | AdminGuestbook, approve: boolean) {
  const label = approve ? '通过' : '拒绝'
  await ElMessageBox.confirm(`确定${label}这条${kind.value === 'guestbook' ? '留言' : '评论'}？`, '提示')
  try {
    if (kind.value === 'guestbook') {
      approve ? await moderationApi.approveGuestbook(row.id) : await moderationApi.rejectGuestbook(row.id)
    } else {
      approve ? await moderationApi.approveComment(row.id) : await moderationApi.rejectComment(row.id)
    }
    ElMessage.success(`${label}成功`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
watch(() => route.path, () => {
  query.page = 1
  load()
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">{{ kind === 'guestbook' ? '留言管理' : '评论审核' }}</h2>
        <p class="page__desc">默认待审核；通过后才会出现在前台</p>
      </div>
    </header>

    <el-card shadow="never" class="panel">
      <div class="filters">
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px" @change="load">
          <el-option label="待审核" :value="0" />
          <el-option label="已通过" :value="1" />
          <el-option label="已拒绝" :value="2" />
        </el-select>
        <el-button @click="load">刷新</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id" size="default">
        <el-table-column v-if="kind === 'comments'" prop="articleTitle" label="文章" min-width="200" show-overflow-tooltip />
        <el-table-column label="用户" width="150">
          <template #default="{ row }">
            <div class="user">
              <span>{{ row.nickname }}</span>
              <span class="muted">{{ row.email }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_META[row.status].type">{{ STATUS_META[row.status].text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" :disabled="row.status === 1" @click="moderate(row, true)">通过</el-button>
            <el-button link type="danger" :disabled="row.status === 2" @click="moderate(row, false)">拒绝</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.size"
          layout="total, prev, pager, next"
          :total="total"
          @current-change="load"
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
}

.user {
  display: flex;
  flex-direction: column;
  font-size: 13px;
}

.muted {
  font-size: 12px;
  color: var(--admin-muted);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
