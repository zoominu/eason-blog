<script setup lang="ts">
import { ref } from 'vue'
import { ArrowLeft, Mail } from 'lucide-vue-next'
import { authApi } from '@/api'

const email = ref('')
const loading = ref(false)
const sent = ref(false)
const error = ref('')

async function submit() {
  if (!email.value.trim()) return
  loading.value = true
  error.value = ''
  try {
    await authApi.forgotPassword(email.value.trim())
    sent.value = true
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-bg" aria-hidden="true" />
    <div class="container narrow auth">
      <RouterLink to="/login" class="back"><ArrowLeft :size="15" /> 返回登录</RouterLink>

      <div class="card panel">
      <h1>找回密码</h1>
      <template v-if="!sent">
        <p class="muted">输入注册邮箱，我们会发送一封包含重置链接的邮件（30 分钟内有效）。</p>
        <form @submit.prevent="submit">
          <label class="label">
            邮箱
            <input v-model="email" type="email" class="input" placeholder="you@example.com" required />
          </label>
          <p v-if="error" class="error">{{ error }}</p>
          <button class="btn btn-primary wide" :disabled="loading">
            <Mail :size="15" /> {{ loading ? '发送中…' : '发送重置链接' }}
          </button>
        </form>
      </template>
      <template v-else>
        <p class="ok">如果该邮箱已注册，重置链接已发送。</p>
        <p class="muted">
          请查收邮件并点击其中的重置链接（30 分钟内有效）。没收到的话，请检查垃圾邮件或稍后重试。
        </p>
        <RouterLink to="/login" class="btn wide">返回登录</RouterLink>
      </template>
    </div>
    </div>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 460px;
  margin: 0 auto;
}

.back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--fg-soft);
  margin-bottom: 16px;
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
  margin: 18px 0 0;
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

code {
  font-size: 12px;
  background: var(--bg-soft);
  padding: 1px 5px;
  border-radius: 4px;
}
</style>
