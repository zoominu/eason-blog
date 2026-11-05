<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { KeyRound } from 'lucide-vue-next'
import { authApi } from '@/api'

const route = useRoute()
const router = useRouter()

const token = computed(() => String(route.query.token ?? ''))
const password = ref('')
const confirm = ref('')
const loading = ref(false)
const done = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!token.value) {
    error.value = '链接缺少 token，请从邮件重新打开'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    await authApi.resetPassword(token.value, password.value)
    done.value = true
    setTimeout(() => router.replace('/login'), 1200)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="container narrow auth">
      <div class="card panel">
      <h1>重置密码</h1>
      <p v-if="done" class="ok">密码已重置，即将跳转到登录页…</p>
      <form v-else @submit.prevent="submit">
        <p class="muted">新密码需 8-72 位，且同时包含字母和数字。</p>
        <label class="label">
          新密码
          <input v-model="password" type="password" class="input" minlength="8" required />
        </label>
        <label class="label">
          确认新密码
          <input v-model="confirm" type="password" class="input" minlength="8" required />
        </label>
        <p v-if="error" class="error">{{ error }}</p>
        <button class="btn btn-primary wide" :disabled="loading">
          <KeyRound :size="15" /> {{ loading ? '提交中…' : '确认重置' }}
        </button>
      </form>
    </div>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 460px;
  margin: 0 auto;
}

.panel {
  padding: 28px 30px;
}

.panel h1 {
  margin: 0 0 8px;
  font-size: 24px;
}

.label {
  display: block;
  font-size: 13px;
  color: var(--fg-soft);
  margin: 16px 0 0;
}

.input {
  display: block;
  width: 100%;
  margin-top: 6px;
  padding: 10px 12px;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--fg);
  font-size: 14px;
  outline: none;
}

.input:focus {
  border-color: var(--accent);
}

.wide {
  width: 100%;
  justify-content: center;
  margin-top: 18px;
}

.ok {
  color: var(--accent);
  font-size: 14px;
}

.error {
  color: #e5484d;
  font-size: 13px;
}
</style>
