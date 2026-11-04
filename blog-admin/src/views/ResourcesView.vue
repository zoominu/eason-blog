<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { resourceApi } from '@/api'
import type { ResourceItem, ResourceRef } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const list = ref<ResourceItem[]>([])
const total = ref(0)
const loading = ref(false)
const uploading = ref(false)

const query = reactive({ page: 1, size: 12, keyword: '', type: '' })

const TYPE_META: Record<string, { label: string; tag: 'success' | 'info' | 'warning' | 'danger' | 'primary' }> = {
  avatar: { label: '头像', tag: 'success' },
  article_image: { label: '文章图片', tag: 'primary' },
  music: { label: '音乐', tag: 'warning' },
  video: { label: '视频', tag: 'danger' },
  document: { label: '文档', tag: 'info' },
  other: { label: '其他', tag: 'info' }
}

const refVisible = ref(false)
const refLoading = ref(false)
const refTitle = ref('')
const refs = ref<ResourceRef[]>([])

async function openRefs(item: ResourceItem) {
  refTitle.value = item.originalName
  refVisible.value = true
  refLoading.value = true
  refs.value = []
  try {
    refs.value = await resourceApi.references(item.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    refLoading.value = false
  }
}

function statusText(status: number) {
  return { 0: '草稿', 1: '已发布', 2: '已下线' }[status] ?? '未知'
}

function sizeText(bytes: number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`
}

async function load() {
  loading.value = true
  try {
    const page = await resourceApi.list({ ...query })
    list.value = page.records
    total.value = page.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function onUpload(options: { file: File }) {
  uploading.value = true
  try {
    await resourceApi.upload(options.file)
    ElMessage.success('上传成功')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    uploading.value = false
  }
}

async function copyUrl(item: ResourceItem) {
  const url = `${window.location.origin}${item.url}`
  try {
    await copyText(url)
    ElMessage.success('链接已复制到剪贴板')
  } catch {
    // 两种方式都失败时弹窗展示 URL，至少让用户能手动复制
    ElMessageBox.alert(url, '复制失败，请手动复制', { confirmButtonText: '知道了' })
  }
}

/**
 * 复制文本：Clipboard API 只在安全上下文可用（HTTPS / localhost），
 * 用 http://192.168.x.x 访问后台时必须回退到 execCommand。
 */
async function copyText(text: string) {
  if (navigator.clipboard && window.isSecureContext) {
    try {
      await navigator.clipboard.writeText(text)
      return
    } catch {
      // 继续走兜底
    }
  }
  const textarea = document.createElement('textarea')
  textarea.value = text
  textarea.setAttribute('readonly', '')
  textarea.style.position = 'fixed'
  textarea.style.top = '-1000px'
  textarea.style.opacity = '0'
  document.body.appendChild(textarea)
  textarea.select()
  textarea.setSelectionRange(0, text.length)
  const ok = document.execCommand('copy')
  document.body.removeChild(textarea)
  if (!ok) throw new Error('copy failed')
}

async function remove(item: ResourceItem) {
  await ElMessageBox.confirm(`确定删除「${item.originalName}」？`, '提示', { type: 'warning' })
  try {
    await resourceApi.remove(item.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">资源管理</h2>
        <p class="page__desc">支持图片 / 音视频 / PDF / 文本 / zip，单文件 ≤ 20MB；被文章引用的资源不能删除</p>
      </div>
      <el-upload :show-file-list="false" :http-request="onUpload" :disabled="uploading">
        <el-button type="primary" :loading="uploading">上传文件</el-button>
      </el-upload>
    </header>

    <el-card shadow="never" class="panel">
      <div class="filters">
        <el-input
          v-model="query.keyword"
          placeholder="按文件名搜索"
          clearable
          style="width: 220px"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button @click="load">查询</el-button>
        <el-select v-model="query.type" placeholder="全部类型" clearable style="width: 140px" @change="() => { query.page = 1; load() }">
          <el-option v-for="(meta, key) in TYPE_META" :key="key" :label="meta.label" :value="key" />
        </el-select>
        <span class="muted total">共 {{ total }} 个文件</span>
      </div>

      <el-empty v-if="!loading && !list.length" description="还没有上传过文件">
        <el-icon :size="28"><UploadFilled /></el-icon>
      </el-empty>

      <div v-else v-loading="loading" class="grid">
        <div v-for="item in list" :key="item.id" class="card-item">
          <div class="thumb">
            <img v-if="item.mimeType.startsWith('image/')" :src="item.url" :alt="item.originalName" />
            <span v-else class="thumb__ext">{{ item.originalName.split('.').pop()?.toUpperCase() }}</span>
          </div>
          <div class="meta">
            <span class="meta__name" :title="item.originalName">{{ item.originalName }}</span>
            <span class="muted">
              <el-tag size="small" :type="TYPE_META[item.type]?.tag ?? 'info'" class="type-tag">
                {{ TYPE_META[item.type]?.label ?? item.type }}
              </el-tag>
              {{ sizeText(item.size) }} · {{ formatDateTime(item.createTime) }}
            </span>
          </div>
          <div class="ops">
            <el-button link type="primary" @click="copyUrl(item)">复制链接</el-button>
            <el-button link type="info" @click="openRefs(item)">
              引用{{ item.articleCount ? ` (${item.articleCount})` : '' }}
            </el-button>
            <el-button link type="danger" @click="remove(item)">删除</el-button>
          </div>
        </div>
      </div>

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

    <el-dialog v-model="refVisible" :title="`引用：${refTitle}`" width="560px">
      <div v-loading="refLoading">
        <p v-if="!refLoading && !refs.length" class="muted">还没有文章引用这个资源。</p>
        <ul v-else class="refs">
          <li v-for="item in refs" :key="item.id" class="refs__item">
            <RouterLink :to="`/articles/${item.id}/edit`" class="refs__link">{{ item.title }}</RouterLink>
            <el-tag size="small" :type="item.status === 1 ? 'success' : 'info'">
              {{ statusText(item.status) }}
            </el-tag>
          </li>
        </ul>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}

.filters {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.total {
  font-size: 12px;
  margin-left: auto;
}

.type-tag {
  margin-right: 6px;
}

.refs {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.refs__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 10px;
  border: 1px solid var(--admin-border);
  border-radius: 8px;
}

.refs__link {
  color: var(--admin-text);
  text-decoration: none;
  font-size: 14px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 14px;
}

.card-item {
  border: 1px solid var(--admin-border);
  border-radius: 10px;
  overflow: hidden;
  background: var(--admin-card);
}

.thumb {
  height: 110px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-lighter);
  overflow: hidden;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb__ext {
  font-size: 13px;
  font-weight: 600;
  color: var(--admin-muted);
}

.meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px 0;
}

.meta__name {
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ops {
  display: flex;
  justify-content: space-between;
  padding: 4px 10px 8px;
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
