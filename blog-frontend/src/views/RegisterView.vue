<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Loader2, UserPlus } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const email = ref('')
const nickname = ref('')
const password = ref('')
const confirm = ref('')
const code = ref('')
const sending = ref(false)
const countdown = ref(0)
const loading = ref(false)
const error = ref('')

async function sendCode() {
  error.value = ''
  if (!email.value.trim()) {
    error.value = '请先输入邮箱'
    return
  }
  sending.value = true
  try {
    await authApi.sendEmailCode(email.value.trim())
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) clearInterval(timer)
    }, 1000)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    sending.value = false
  }
}

const passwordValid = computed(() => password.value.length >= 8 && /[A-Za-z]/.test(password.value) && /\d/.test(password.value))

async function submit() {
  error.value = ''
  if (!email.value.trim()) {
    error.value = '请输入邮箱'
    return
  }
  if (!passwordValid.value) {
    error.value = '密码至少 8 位，且同时包含字母和数字'
    return
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的密码不一致'
    return
  }

  loading.value = true
  try {
    await userStore.register({
      email: email.value.trim(),
      password: password.value,
      nickname: nickname.value.trim() || undefined,
      code: code.value.trim() || undefined
    })
    // 注册成功后直接登录，减少一次手动操作
    await userStore.login(email.value.trim(), password.value)
    router.replace('/')
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
    <div class="container auth">
      <div class="card panel">
        <h1>注册</h1>
        <p class="muted">注册后可以评论、收藏和留言，我们会发一封验证邮件。</p>

      <form class="form" @submit.prevent="submit">
        <label class="field">
          <span>邮箱</span>
          <input v-model="email" type="email" autocomplete="email" placeholder="you@example.com" />
        </label>

        <label class="field">
          <span>邮箱验证码</span>
          <div class="code-row">
            <input v-model="code" type="text" maxlength="6" inputmode="numeric" placeholder="6 位验证码" />
            <button type="button" class="btn btn-sm" :disabled="sending || countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : sending ? '发送中…' : '发送验证码' }}
            </button>
          </div>
        </label>

        <label class="field">
          <span>昵称（可选）</span>
          <input v-model="nickname" type="text" maxlength="50" placeholder="展示名称" />
        </label>

        <label class="field">
          <span>密码</span>
          <input v-model="password" type="password" autocomplete="new-password" placeholder="至少 8 位，含字母和数字" />
        </label>

        <label class="field">
          <span>确认密码</span>
          <input v-model="confirm" type="password" autocomplete="new-password" placeholder="再输入一次" />
        </label>

        <p v-if="error" class="error">{{ error }}</p>

        <button class="btn btn-primary submit" type="submit" :disabled="loading">
          <Loader2 v-if="loading" :size="16" class="spin" />
          <UserPlus v-else :size="16" />
          注册并登录
        </button>
      </form>

      <p class="switch muted">
        已有账号？
        <RouterLink to="/login" class="link">去登录</RouterLink>
      </p>
    </div>
    </div>
  </div>
</template>

<style scoped>
.auth {
  max-width: 460px;
  margin: 0 auto;
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
