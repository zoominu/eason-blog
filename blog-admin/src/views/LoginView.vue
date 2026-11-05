<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { FormInstance, FormRules } from 'element-plus'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ account: '', password: '' })

const rules: FormRules = {
  account: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await userStore.login(form.account.trim(), form.password)
    if (!user.roles.includes('SUPER_ADMIN')) {
      ElMessage.error('该账号不是管理员')
      await userStore.logout()
      return
    }
    ElMessage.success('登录成功')
    router.replace(String(route.query.redirect || '/dashboard'))
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login">
    <div class="login__panel">
      <div class="login__brand">
        <span class="dot" />
        Eason Blog
        <span class="tag">Admin</span>
      </div>
      <h1>登录管理后台</h1>
      <p class="muted">仅站点管理员可访问</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号" prop="account">
          <el-input v-model="form.account" placeholder="管理员账号" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            size="large"
            @keyup.enter="submit"
          />
        </el-form-item>
        <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--admin-bg);
  padding: 24px;
}

.login__panel {
  width: 100%;
  max-width: 400px;
  background: var(--admin-card);
  border: 1px solid var(--admin-border);
  border-radius: 16px;
  padding: 32px;
}

.login__brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  margin-bottom: 18px;
}

.dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #2f6bff;
}

.tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(47, 107, 255, 0.12);
  color: #2f6bff;
}

h1 {
  margin: 0 0 4px;
  font-size: 20px;
}

.muted {
  margin: 0 0 20px;
  font-size: 13px;
  color: var(--admin-muted);
}

.submit {
  width: 100%;
  margin-top: 4px;
}
</style>
