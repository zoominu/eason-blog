<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { resourceApi } from '@/api'
import type { ResourceItem } from '@/api/types'
import { formatDateTime } from '@/utils/format'

const list = ref<ResourceItem[]>([])
const total = ref(0)
const loading = ref(false)
const uploading = ref(false)

const query = reactive({ page: 1, size: 12, keyword: '' })

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
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.warning(url)
  }
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
        <p class="page__desc">支持图片 / PDF / 文本 / zip，单文件 ≤ 5MB；被文章引用的资源不能删除</p>
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
            <span class="muted">{{ sizeText(item.size) }} · {{ formatDateTime(item.createTime) }}</span>
          </div>
          <div class="ops">
            <el-button link type="primary" @click="copyUrl(item)">复制链接</el-button>
            <el-button link type="danger" @click="remove(item)">删除</el-button>
          </div>
        </div>
      </div>

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
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.total {
  font-size: 12px;
  margin-left: auto;
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
