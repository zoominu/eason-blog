<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { logApi } from '@/api'
import type { AdminLoginLog, AdminOperationLog } from '@/api/types'

const tab = ref<'login' | 'operation'>('login')
const loading = ref(false)

const loginLogs = ref<AdminLoginLog[]>([])
const loginTotal = ref(0)
const loginQuery = reactive({ page: 1, size: 20, status: undefined as number | undefined, keyword: '' })

const operationLogs = ref<AdminOperationLog[]>([])
const operationTotal = ref(0)
const operationQuery = reactive({ page: 1, size: 20, module: '' })

async function loadLogin() {
  loading.value = true
  try {
    const page = await logApi.loginLogs({ ...loginQuery })
    loginLogs.value = page.records
    loginTotal.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function loadOperation() {
  loading.value = true
  try {
    const page = await logApi.operationLogs({ ...operationQuery })
    operationLogs.value = page.records
    operationTotal.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function switchTab(next: 'login' | 'operation') {
  tab.value = next
  next === 'login' ? loadLogin() : loadOperation()
}

onMounted(loadLogin)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">日志审计</h2>
        <p class="page__desc">登录记录含失败尝试；操作日志只记录关键写操作</p>
      </div>
    </header>

    <el-card shadow="never" class="panel">
      <el-tabs v-model="tab" @tab-change="switchTab(tab)">
        <el-tab-pane label="登录日志" name="login">
          <div class="filters">
            <el-input
              v-model="loginQuery.keyword"
              placeholder="按邮箱搜索"
              clearable
              style="width: 220px"
              @keyup.enter="loadLogin"
              @clear="loadLogin"
            />
            <el-select
              v-model="loginQuery.status"
              placeholder="全部结果"
              clearable
              style="width: 130px"
              @change="loadLogin"
            >
              <el-option label="成功" :value="1" />
              <el-option label="失败" :value="0" />
            </el-select>
            <el-button @click="loadLogin">查询</el-button>
          </div>

          <el-table v-loading="loading" :data="loginLogs" size="small">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column prop="email" label="账号" min-width="180" show-overflow-tooltip />
            <el-table-column label="IP" width="140">
              <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.loginStatus === 1 ? 'success' : 'danger'">
                  {{ row.loginStatus === 1 ? '成功' : '失败' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="150" />
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="loginQuery.page"
              v-model:page-size="loginQuery.size"
              layout="total, sizes, prev, pager, next"
              :page-sizes="[10, 20, 50, 100]"
              :total="loginTotal"
              @current-change="loadLogin"
              @size-change="() => { loginQuery.page = 1; loadLogin() }"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="操作日志" name="operation">
          <div class="filters">
            <el-input
              v-model="operationQuery.module"
              placeholder="模块，如 article"
              clearable
              style="width: 220px"
              @keyup.enter="loadOperation"
              @clear="loadOperation"
            />
            <el-button @click="loadOperation">查询</el-button>
          </div>

          <el-table v-loading="loading" :data="operationLogs" size="small">
            <el-table-column label="时间" width="170">
              <template #default="{ row }">{{ row.createTime?.replace('T', ' ') }}</template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="120" />
            <el-table-column prop="module" label="模块" width="110" />
            <el-table-column prop="action" label="动作" width="150" />
            <el-table-column prop="detail" label="请求" min-width="200" show-overflow-tooltip />
            <el-table-column label="IP" width="130">
              <template #default="{ row }"><span class="mono">{{ row.ip || '—' }}</span></template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="operationQuery.page"
              v-model:page-size="operationQuery.size"
              layout="total, sizes, prev, pager, next"
              :page-sizes="[10, 20, 50, 100]"
              :total="operationTotal"
              @current-change="loadOperation"
              @size-change="() => { operationQuery.page = 1; loadOperation() }"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
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

.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
