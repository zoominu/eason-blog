<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { settingApi } from '@/api'
import type { SystemConfigItem } from '@/api/types'

const items = ref<SystemConfigItem[]>([])
const draft = ref<Record<string, string>>({})
const loading = ref(true)
const saving = ref(false)
/** 当前展开的分组（手风琴，一次只展开一组） */
const activeGroup = ref('')

/**
 * 固定取值项用下拉，避免手输 true/false/local 拼错。
 * key 命中即用 options；没命中的仍然是普通输入框。
 */
const SELECT_OPTIONS: Record<string, { label: string; value: string }[]> = {
  'comment.audit': [
    { label: '需要审核后展示', value: 'true' },
    { label: '直接展示', value: 'false' }
  ],
  'guestbook.audit': [
    { label: '需要审核后展示', value: 'true' },
    { label: '直接展示', value: 'false' }
  ],
  'register.enabled': [
    { label: '开放注册', value: 'true' },
    { label: '关闭注册', value: 'false' }
  ],
  'storage.driver': [
    { label: '本地磁盘', value: 'local' },
    { label: '对象存储（未实现）', value: 'oss' }
  ],
  'audit.banned-action': [
    { label: '转人工审核（默认）', value: 'review' },
    { label: '替换为 * 后展示', value: 'mask' }
  ],
  'site.theme': [
    { label: '跟随系统', value: 'system' },
    { label: '浅色', value: 'light' },
    { label: '深色', value: 'dark' }
  ]
}

/** 以 .enabled / .audit 结尾的布尔配置也走下拉 */
function optionsFor(key: string) {
  if (SELECT_OPTIONS[key]) return SELECT_OPTIONS[key]
  if (key.endsWith('.enabled') || key.endsWith('.audit')) {
    return [
      { label: '是', value: 'true' },
      { label: '否', value: 'false' }
    ]
  }
  return null
}

/** 分享模板、违禁词这类多行文本用 textarea */
function isMultiline(item: SystemConfigItem) {
  return (
    item.key === 'share.template' ||
    item.key === 'audit.banned-words' ||
    (item.description ?? '').includes('模板')
  )
}

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
    audit: '审核与违禁词',
    share: '分享',
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
    // 手风琴默认展开第一组
    if (!activeGroup.value) {
      activeGroup.value = groups.value[0]?.prefix ?? ''
    }
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

    <el-card v-else class="panel" shadow="never">
      <el-collapse v-model="activeGroup" accordion class="settings-collapse">
        <el-collapse-item v-for="group in groups" :key="group.prefix" :name="group.prefix">
          <template #title>
            <span class="panel__title">{{ group.label }}</span>
            <span class="panel__count">{{ group.list.length }} 项</span>
          </template>
          <el-form label-position="top">
            <el-form-item v-for="item in group.list" :key="item.key" :label="item.description || item.key">
              <el-select
                v-if="optionsFor(item.key)"
                v-model="draft[item.key]"
                style="width: 100%"
                placeholder="请选择"
              >
                <el-option
                  v-for="option in optionsFor(item.key) ?? []"
                  :key="option.value"
                  :label="option.label"
                  :value="option.value"
                />
              </el-select>
              <el-input
                v-else-if="isMultiline(item)"
                v-model="draft[item.key]"
                type="textarea"
                :rows="3"
                :placeholder="item.key"
              />
              <el-input v-else v-model="draft[item.key]" :placeholder="item.key" />
              <p class="hint">
                <span class="mono">{{ item.key }}</span>
                <el-tag v-if="item.isPublic === 1" size="small" type="success" class="tag">公开</el-tag>
                <el-tag v-else size="small" type="info" class="tag">仅后台</el-tag>
                <span v-if="item.key === 'share.template'" class="tag">支持 {title} {url} {summary}</span>
              </p>
            </el-form-item>
          </el-form>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
  margin-bottom: 16px;
}

.settings-collapse {
  border: none;
}

.settings-collapse :deep(.el-collapse-item__header) {
  font-size: 14px;
  font-weight: 500;
}

.settings-collapse :deep(.el-collapse-item__content) {
  padding-bottom: 8px;
}

.panel__count {
  margin-left: 8px;
  font-size: 12px;
  font-weight: 400;
  color: var(--admin-muted);
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
