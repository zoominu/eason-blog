<!--
  后台角色权限：角色列表与权限勾选，保存后影响对应管理员的接口权限。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api'
import type { PermissionItem, RoleItem } from '@/api/types'

const roles = ref<RoleItem[]>([])
const permissions = ref<PermissionItem[]>([])
const loading = ref(true)
const saving = ref<number | null>(null)

async function load() {
  loading.value = true
  try {
    const [roleList, permissionList] = await Promise.all([roleApi.list(), roleApi.permissions()])
    roles.value = roleList
    permissions.value = permissionList
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function save(role: RoleItem) {
  saving.value = role.id
  try {
    await roleApi.updatePermissions(role.id, role.permissionIds)
    ElMessage.success(`「${role.name}」权限已保存`)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    saving.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="page">
    <header class="page__head">
      <div>
        <h2 class="page__title">角色权限</h2>
        <p class="page__desc">
          接口级鉴权目前以角色为准（SUPER_ADMIN / USER），权限点用于后台能力矩阵与后续细化
        </p>
      </div>
    </header>

    <el-skeleton v-if="loading" :rows="4" animated />

    <div v-else class="grid">
      <el-card v-for="role in roles" :key="role.id" class="role" shadow="never">
        <template #header>
          <div class="role__head">
            <div>
              <span class="role__name">{{ role.name }}</span>
              <span class="role__code">{{ role.code }}</span>
            </div>
            <el-button type="primary" size="small" :loading="saving === role.id" @click="save(role)">
              保存
            </el-button>
          </div>
        </template>
        <p class="muted role__desc">{{ role.description || '暂无描述' }}</p>
        <el-checkbox-group v-model="role.permissionIds" class="perms">
          <el-checkbox v-for="perm in permissions" :key="perm.id" :value="perm.id">
            {{ perm.name }}
            <span class="perm-code">{{ perm.code }}</span>
          </el-checkbox>
        </el-checkbox-group>
      </el-card>
    </div>
  </div>
</template>

<style scoped>
.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(340px, 1fr));
  gap: 16px;
}

.role {
  border-radius: 12px;
}

.role__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.role__name {
  font-weight: 600;
  margin-right: 8px;
}

.role__code {
  font-size: 12px;
  color: var(--admin-muted);
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.role__desc {
  margin: 0 0 12px;
  font-size: 13px;
}

.perms {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.perm-code {
  margin-left: 6px;
  font-size: 11px;
  color: var(--admin-muted);
}

.muted {
  color: var(--admin-muted);
}
</style>
