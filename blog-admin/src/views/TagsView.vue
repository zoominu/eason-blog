<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { tagApi } from '@/api'
import type { Tag } from '@/api/types'

const list = ref<Tag[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({ name: '', slug: '' })
const dialogTitle = computed(() => (editingId.value ? '编辑标签' : '新建标签'))

async function load() {
  loading.value = true
  try {
    list.value = await tagApi.list()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', slug: '' })
  dialogVisible.value = true
}

function openEdit(row: Tag) {
  editingId.value = row.id
  Object.assign(form, { name: row.name, slug: row.slug })
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写标签名称')
    return
  }
  saving.value = true
  try {
    const payload = { name: form.name.trim(), slug: form.slug.trim() || undefined }
    if (editingId.value) {
      await tagApi.update(editingId.value, payload)
    } else {
      await tagApi.create(payload)
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

async function remove(row: Tag) {
  await ElMessageBox.confirm(`确定删除标签「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await tagApi.remove(row.id)
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
        <h2 class="page__title">标签管理</h2>
        <p class="page__desc">被文章引用的标签无法删除，需先解除关联</p>
      </div>
      <el-button type="primary" @click="openCreate">新建标签</el-button>
    </header>

    <el-card shadow="never" class="panel">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="slug" label="Slug" min-width="200" />
        <el-table-column prop="articleCount" label="文章数" width="120" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="420px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="form.slug" placeholder="留空自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}
</style>
