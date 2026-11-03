<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { columnApi, articleApi } from '@/api'
import type { Column } from '@/api/types'

const list = ref<Column[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({ name: '', slug: '', description: '', cover: '', sort: 0 })
const dialogTitle = computed(() => (editingId.value ? '编辑专栏' : '新建专栏'))

async function load() {
  loading.value = true
  try {
    list.value = await columnApi.list()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', slug: '', description: '', cover: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: Column) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    slug: row.slug,
    description: row.description ?? '',
    cover: row.cover ?? '',
    // 回填排序：之前固定写 0，保存一次就把原有顺序重置了
    sort: row.sort ?? 0
  })
  dialogVisible.value = true
}

const articleDialogVisible = ref(false)
const articleOptions = ref<{ id: number; title: string }[]>([])
const selectedArticleIds = ref<number[]>([])
const editingColumn = ref<Column | null>(null)
const savingArticles = ref(false)

async function openArticles(row: Column) {
  editingColumn.value = row
  articleDialogVisible.value = true
  try {
    const [page, linked] = await Promise.all([
      articleApi.page({ page: 1, size: 50 }),
      columnApi.articles(row.id)
    ])
    articleOptions.value = page.records.map((a) => ({ id: a.id, title: a.title }))
    selectedArticleIds.value = linked.map((a) => a.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function saveArticles() {
  if (!editingColumn.value) return
  savingArticles.value = true
  try {
    await columnApi.setArticles(editingColumn.value.id, selectedArticleIds.value)
    ElMessage.success('专栏文章已更新')
    articleDialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    savingArticles.value = false
  }
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写专栏名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      slug: form.slug.trim() || undefined,
      description: form.description || undefined,
      cover: form.cover || undefined,
      sort: form.sort
    }
    if (editingId.value) {
      await columnApi.update(editingId.value, payload)
    } else {
      await columnApi.create(payload)
    }
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = false
  }
}

async function remove(row: Column) {
  await ElMessageBox.confirm(`确定删除专栏「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await columnApi.remove(row.id)
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
        <h2 class="page__title">专栏管理</h2>
        <p class="page__desc">专栏内的文章排序在 P3 与前台专栏页一起完善</p>
      </div>
      <el-button type="primary" @click="openCreate">新建专栏</el-button>
    </header>

    <el-card shadow="never" class="panel">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="slug" label="Slug" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="220">
          <template #default="{ row }">
            <span class="muted">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="100" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openArticles(row)">文章</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="100" />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="form.slug" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" maxlength="500" />
        </el-form-item>
        <el-form-item label="封面图地址">
          <el-input v-model="form.cover" placeholder="可留空" />
        </el-form-item>
        <el-form-item label="排序（越小越靠前）">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="articleDialogVisible" :title="`管理文章 · ${editingColumn?.name ?? ''}`" width="560px">
      <p class="muted hint">选择顺序即专栏内的阅读顺序，可多选。</p>
      <el-select
        v-model="selectedArticleIds"
        multiple
        filterable
        placeholder="选择文章"
        style="width: 100%"
      >
        <el-option v-for="a in articleOptions" :key="a.id" :label="a.title" :value="a.id" />
      </el-select>
      <template #footer>
        <el-button @click="articleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingArticles" @click="saveArticles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}

.muted {
  color: var(--admin-muted);
}

.hint {
  margin: 0 0 10px;
  font-size: 13px;
}
</style>
