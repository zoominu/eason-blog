<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { roleApi, userApi } from '@/api'
import type { AdminUser, RoleItem } from '@/api/types'

const list = ref<AdminUser[]>([])
const roles = ref<RoleItem[]>([])
const total = ref(0)
const loading = ref(false)

const query = reactive({ page: 1, size: 10, keyword: '', status: undefined as number | undefined })

const dialogVisible = ref(false)
const editing = ref<AdminUser | null>(null)
const selectedRoles = ref<number[]>([])
const saving = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await userApi.list({ ...query })
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

async function toggleStatus(row: AdminUser) {
  const action = row.status === 1 ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.nickname}」？`, '提示', { type: 'warning' })
  try {
    row.status === 1 ? await userApi.disable(row.id) : await userApi.enable(row.id)
    ElMessage.success(`${action}成功`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function openRoles(row: AdminUser) {
  editing.value = row
  selectedRoles.value = roles.value
    .filter((r) => row.roles.includes(r.code))
    .map((r) => r.id)
  dialogVisible.value = true
}

async function saveRoles() {
  if (!editing.value) return
  saving.value = true
  try {
    await userApi.assignRoles(editing.value.id, selectedRoles.value)
    ElMessage.success('角色已更新')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await load()
  try {
    roles.value = await roleApi.list()
  } catch {
    // 角色列表只用于弹窗，失败不阻塞页面
  }
})
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">用户管理</h2>
        <p class="page__desc">禁用后该用户立即无法登录（JWT 每次请求都会校验状态）</p>
      </div>
    </header>

    <el-card shadow="never" class="panel">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="邮箱或昵称"
          clearable
          style="width: 220px"
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px" @change="search">
          <el-option label="正常" :value="1" />
          <el-option label="已禁用" :value="0" />
        </el-select>
        <el-button @click="search">查询</el-button>
      </div>

      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column label="用户" min-width="200">
          <template #default="{ row }">
            <div class="user">
              <span class="user__name">{{ row.nickname }}</span>
              <span class="muted">{{ row.email }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="角色" width="160">
          <template #default="{ row }">
            <el-tag
              v-for="role in row.roles"
              :key="role"
              size="small"
              :type="role === 'SUPER_ADMIN' ? 'danger' : 'info'"
              class="role-tag"
            >
              {{ role === 'SUPER_ADMIN' ? '管理员' : '用户' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '正常' : '已禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后登录" width="170">
          <template #default="{ row }">{{ row.lastLoginTime?.replace('T', ' ') || '—' }}</template>
        </el-table-column>
        <el-table-column label="IP" width="130">
          <template #default="{ row }"><span class="mono">{{ row.lastLoginIp || '—' }}</span></template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openRoles(row)">角色</el-button>
            <el-button link :type="row.status === 1 ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 1 ? '禁用' : '启用' }}
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

    <el-dialog v-model="dialogVisible" title="分配角色" width="420px">
      <p class="muted">用户：{{ editing?.email }}</p>
      <el-checkbox-group v-model="selectedRoles" class="roles">
        <el-checkbox v-for="role in roles" :key="role.id" :value="role.id">
          {{ role.name }}（{{ role.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
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
}

.user__name {
  font-size: 14px;
}

.muted {
  font-size: 12px;
  color: var(--admin-muted);
}

.role-tag {
  margin-right: 4px;
}

.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.roles {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
