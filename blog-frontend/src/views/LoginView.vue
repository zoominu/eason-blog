<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loader2, LogIn } from 'lucide-vue-next'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const email = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

async function submit() {
  error.value = ''
  if (!email.value || !password.value) {
    error.value = '请输入邮箱和密码'
    return
  }
  loading.value = true
  try {
    await userStore.login(email.value.trim(), password.value)
    const redirect = String(route.query.redirect || '/')
    router.replace(redirect)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="container auth">
    <div class="card panel">
      <h1>登录</h1>
      <p class="muted">使用邮箱登录，登录后可评论、收藏与留言。</p>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span>邮箱</span>
          <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
        </label>

        <label class="field">
          <span>密码</span>
          <input
            v-model="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
          />
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="loading">
          <Loader2 v-if="loading" :size="16" class="spin" />
          <LogIn v-else :size="16" />
          登录
        </button>
      </form>

      <p class="switch muted">
        还没有账号？
        <RouterLink to="/register" class="link">注册</RouterLink>
      </p>
    </div>
  </div>
</template>

<style scoped>
.auth {
  max-width: 460px;
  padding-top: 24px;
}

.panel {
  padding: 32px;
}

.panel h1 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 22px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--fg-soft);
}

.field input {
  height: 42px;
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--fg);
  padding: 0 14px;
  font-size: 14px;
  outline: none;
}

.field input:focus {
  border-color: var(--accent);
}

.submit {
  justify-content: center;
  height: 42px;
  margin-top: 6px;
}

.submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.error {
  margin: 0;
  color: #e5484d;
  font-size: 13px;
}

.switch {
  margin: 18px 0 0;
  font-size: 13px;
  text-align: center;
}

.link {
  color: var(--accent);
}

.spin {
  animation: spin 0.9s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
