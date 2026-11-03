<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
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
const selected = ref<(AdminComment | AdminGuestbook)[]>([])
const highlightId = ref<number | null>(null)

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
    // 从通知跳转：?highlight=ID。该评论可能已通过审核，
    // 所以要把状态筛选清掉、放大页容量，才可能出现在当前结果里。
    const target = Number(route.query.highlight ?? 0)
    if (target && query.status === 0) {
      query.status = undefined
      query.page = 1
      query.size = 100
    }
    const page =
      kind.value === 'guestbook'
        ? await moderationApi.guestbook({ ...query })
        : await moderationApi.comments({ ...query })
    list.value = page.records
    total.value = page.total

    if (target) {
      highlightId.value = target
      await nextTick()
      const row = document.querySelector('.el-table__row--highlight')
      row?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      setTimeout(() => (highlightId.value = null), 4000)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function rowClassName({ row }: { row: AdminComment | AdminGuestbook }) {
  return highlightId.value === row.id ? 'el-table__row--highlight' : ''
}

/** 给行加 data-comment-id，方便按 id 滚动定位 */
function rowProps({ row }: { row: AdminComment | AdminGuestbook }) {
  return { 'data-comment-id': String(row.id) }
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

function onSelectionChange(rows: (AdminComment | AdminGuestbook)[]) {
  selected.value = rows
}

async function batchModerate(approve: boolean) {
  if (!selected.value.length) return
  const label = approve ? '通过' : '拒绝'
  await ElMessageBox.confirm(
    `确定批量${label}选中的 ${selected.value.length} 条${kind.value === 'guestbook' ? '留言' : '评论'}？`,
    '批量操作'
  )
  try {
    const ids = selected.value.map((row) => row.id)
    const result = kind.value === 'guestbook'
      ? await moderationApi.batchGuestbook(ids, approve)
      : await moderationApi.batchComments(ids, approve)
    ElMessage.success(`已${label} ${result.updated} 条`)
    selected.value = []
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
watch(() => route.path, () => {
  query.page = 1
  highlightId.value = null
  load()
})

const replyVisible = ref(false)
const replyText = ref('')
const replyRow = ref<AdminGuestbook | null>(null)
const savingReply = ref(false)

function openReply(row: AdminGuestbook) {
  replyRow.value = row
  replyText.value = row.reply ?? ''
  replyVisible.value = true
}

async function submitReply() {
  if (!replyRow.value || !replyText.value.trim()) return
  savingReply.value = true
  try {
    await moderationApi.replyGuestbook(replyRow.value.id, replyText.value.trim())
    ElMessage.success('回复已保存并通知用户')
    replyVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    savingReply.value = false
  }
}
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
        <el-button type="success" plain :disabled="!selected.length" @click="batchModerate(true)">
          批量通过{{ selected.length ? ` (${selected.length})` : '' }}
        </el-button>
        <el-button type="danger" plain :disabled="!selected.length" @click="batchModerate(false)">
          批量拒绝{{ selected.length ? ` (${selected.length})` : '' }}
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        size="default"
        :row-class-name="rowClassName"
        :row-props="rowProps"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="44" />
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
        <el-table-column v-if="kind === 'guestbook'" label="回复" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="muted">{{ row.reply || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="STATUS_META[row.status].type">{{ STATUS_META[row.status].text }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
        </el-table-column>
        <el-table-column label="操作" :width="kind === 'guestbook' ? 210 : 150" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="kind === 'guestbook'"
              link
              type="primary"
              @click="openReply(row)"
            >
              回复
            </el-button>
            <el-button link type="success" :disabled="row.status === 1" @click="moderate(row, true)">通过</el-button>
            <el-button link type="danger" :disabled="row.status === 2" @click="moderate(row, false)">拒绝</el-button>
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

    <el-dialog v-model="replyVisible" title="回复留言" width="480px">
      <p class="muted reply-quote">{{ replyRow?.content }}</p>
      <el-input v-model="replyText" type="textarea" :rows="4" maxlength="1000" placeholder="回复会展示在留言下方，并通知留言者" />
      <template #footer>
        <el-button @click="replyVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingReply" @click="submitReply">保存回复</el-button>
      </template>
    </el-dialog>
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

.reply-quote {
  margin: 0 0 12px;
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--el-fill-color-light);
  font-size: 13px;
}

:deep(.el-table__row--highlight) {
  animation: row-flash 3.5s ease;
}

@keyframes row-flash {
  0%,
  60% {
    background: color-mix(in srgb, var(--el-color-primary) 16%, transparent);
  }
  100% {
    background: transparent;
  }
}
</style>
