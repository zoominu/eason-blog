<!--
  后台分类管理：树形列表、新增/编辑/删除；删除前提示子分类与文章引用。
-->
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi } from '@/api'
import type { Category } from '@/api/types'

const list = ref<Category[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)

const form = reactive({
  name: '',
  slug: '',
  parentId: 0,
  description: '',
  sort: 0
})

const dialogTitle = computed(() => (editingId.value ? '编辑分类' : '新建分类'))

async function load() {
  loading.value = true
  try {
    list.value = await categoryApi.list()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', slug: '', parentId: 0, description: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: Category) {
  editingId.value = row.id
  Object.assign(form, {
    name: row.name,
    slug: row.slug,
    parentId: row.parentId,
    description: row.description ?? '',
    sort: 0
  })
  dialogVisible.value = true
}

async function submit() {
  if (!form.name.trim()) {
    ElMessage.warning('请填写分类名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      name: form.name.trim(),
      slug: form.slug.trim() || undefined,
      parentId: form.parentId || 0,
      description: form.description || undefined,
      sort: form.sort
    }
    if (editingId.value) {
      await categoryApi.update(editingId.value, payload)
    } else {
      await categoryApi.create(payload)
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

async function remove(row: Category) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await categoryApi.remove(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    // 后端会拒绝删除仍有文章的引用，这里原样提示
    ElMessage.error((e as Error).message)
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">分类管理</h2>
        <p class="page__desc">分类下有文章时无法删除，需要先移动文章</p>
      </div>
      <el-button type="primary" @click="openCreate">新建分类</el-button>
    </header>

    <el-card shadow="never" class="panel">
      <el-table v-loading="loading" :data="list" row-key="id">
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="slug" label="Slug" min-width="160" />
        <el-table-column label="父分类" width="140">
          <template #default="{ row }">
            {{ row.parentId ? list.find((c) => c.id === row.parentId)?.name ?? '—' : '根分类' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200">
          <template #default="{ row }">
            <span class="muted">{{ row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="articleCount" label="文章数" width="100" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="50" />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="form.slug" placeholder="留空自动生成" />
        </el-form-item>
        <el-form-item label="父分类">
          <el-select v-model="form.parentId" style="width: 100%">
            <el-option label="根分类" :value="0" />
            <el-option
              v-for="c in list.filter((x) => x.id !== editingId)"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
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
  </div>
</template>

<style scoped>
.panel {
  border-radius: 12px;
}

.muted {
  color: var(--admin-muted);
}
</style>
