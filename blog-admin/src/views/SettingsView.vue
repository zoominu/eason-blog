<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingApi } from '@/api'
import type { SystemConfigItem } from '@/api/types'

const items = ref<SystemConfigItem[]>([])
const draft = ref<Record<string, string>>({})
const loading = ref(true)
const saving = ref(false)

const groups = computed(() => {
  const map = new Map<string, SystemConfigItem[]>()
  for (const item of items.value) {
    const prefix = item.key.includes('.') ? item.key.split('.')[0] : '其他'
    if (!map.has(prefix)) map.set(prefix, [])
    map.get(prefix)!.push(item)
  }
  const labels: Record<string, string> = {
    site: '站点信息',
    comment: '评论',
    guestbook: '留言',
    register: '注册',
    storage: '存储',
    其他: '其他'
  }
  return [...map.entries()].map(([prefix, list]) => ({
    prefix,
    label: labels[prefix] ?? prefix,
    list
  }))
})

const dirty = computed(() =>
  items.value.some((item) => (item.value ?? '') !== (draft.value[item.key] ?? ''))
)

async function load() {
  loading.value = true
  try {
    items.value = await settingApi.list()
    const next: Record<string, string> = {}
    for (const item of items.value) next[item.key] = item.value ?? ''
    draft.value = next
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    const changed: Record<string, string> = {}
    for (const item of items.value) {
      const value = draft.value[item.key] ?? ''
      if (value !== (item.value ?? '')) changed[item.key] = value
    }
    if (!Object.keys(changed).length) {
      ElMessage.info('没有修改')
      return
    }
    const result = await settingApi.update(changed)
    ElMessage.success(`已保存 ${result.updated} 项`)
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">系统设置</h2>
        <p class="page__desc">修改后前台即时生效（公开项才会出现在 /site/config）</p>
      </div>
      <el-button type="primary" :loading="saving" :disabled="!dirty && !loading" @click="save">
        保存修改
      </el-button>
    </header>

    <el-skeleton v-if="loading" :rows="6" animated />

    <el-card v-for="group in groups" v-else :key="group.prefix" class="panel" shadow="never">
      <template #header><span class="panel__title">{{ group.label }}</span></template>
      <el-form label-position="top">
        <el-form-item v-for="item in group.list" :key="item.key" :label="item.description || item.key">
          <el-input v-model="draft[item.key]" :placeholder="item.key" />
          <p class="hint">
            <span class="mono">{{ item.key }}</span>
            <el-tag v-if="item.isPublic === 1" size="small" type="success" class="tag">公开</el-tag>
            <el-tag v-else size="small" type="info" class="tag">仅后台</el-tag>
          </p>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
  margin-bottom: 16px;
}

.panel__title {
  font-size: 14px;
  font-weight: 500;
}

.hint {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 4px 0 0;
  font-size: 12px;
  color: var(--admin-muted);
}

.mono {
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.tag {
  transform: scale(0.9);
}
</style>
