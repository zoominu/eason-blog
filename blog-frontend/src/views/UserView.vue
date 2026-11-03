<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { LogOut, Save } from 'lucide-vue-next'
import { authApi } from '@/api'
import { useUserStore } from '@/stores/user'
import { formatDate } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const nickname = ref('')
const bio = ref('')
const saving = ref(false)
const message = ref('')
const error = ref('')

const oldPassword = ref('')
const newPassword = ref('')
const changing = ref(false)
const pwdMessage = ref('')
const pwdError = ref('')

onMounted(async () => {
  await userStore.fetchMe()
  nickname.value = userStore.user?.nickname ?? ''
  bio.value = userStore.user?.bio ?? ''
})

async function saveProfile() {
  saving.value = true
  message.value = ''
  error.value = ''
  try {
    const updated = await authApi.updateProfile({ nickname: nickname.value, bio: bio.value })
    userStore.user = updated
    message.value = '资料已更新'
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    saving.value = false
  }
}

async function changePassword() {
  changing.value = true
  pwdMessage.value = ''
  pwdError.value = ''
  try {
    await authApi.changePassword(oldPassword.value, newPassword.value)
    pwdMessage.value = '密码已修改，请重新登录'
    oldPassword.value = ''
    newPassword.value = ''
  } catch (e) {
    pwdError.value = (e as Error).message
  } finally {
    changing.value = false
  }
}

async function logout() {
  await userStore.logout()
  router.replace('/')
}
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>个人中心</h1>
      <p class="muted" v-if="userStore.user">
        {{ userStore.user.email }} · 注册于 {{ formatDate(userStore.user.registerTime) }}
      </p>
    </header>

    <section class="card block">
      <h2>资料</h2>
      <label class="field">
        <span>昵称</span>
        <input v-model="nickname" maxlength="50" />
      </label>
      <label class="field">
        <span>简介</span>
        <textarea v-model="bio" rows="3" maxlength="500" />
      </label>
      <p v-if="message" class="ok">{{ message }}</p>
      <p v-if="error" class="error">{{ error }}</p>
      <button class="btn btn-primary" :disabled="saving" @click="saveProfile">
        <Save :size="15" /> {{ saving ? '保存中…' : '保存资料' }}
      </button>
    </section>

    <section class="card block">
      <h2>安全</h2>
      <label class="field">
        <span>当前密码</span>
        <input v-model="oldPassword" type="password" autocomplete="current-password" />
      </label>
      <label class="field">
        <span>新密码</span>
        <input v-model="newPassword" type="password" autocomplete="new-password" placeholder="至少 8 位，含字母和数字" />
      </label>
      <p v-if="pwdMessage" class="ok">{{ pwdMessage }}</p>
      <p v-if="pwdError" class="error">{{ pwdError }}</p>
      <button class="btn" :disabled="changing" @click="changePassword">
        {{ changing ? '修改中…' : '修改密码' }}
      </button>
    </section>

    <button class="btn logout" @click="logout"><LogOut :size="15" /> 退出登录</button>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 680px;
}

.head {
  margin-bottom: 24px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.block {
  padding: 24px 26px;
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.block h2 {
  margin: 0 0 4px;
  font-size: 16px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--fg-soft);
}

.field input,
.field textarea {
  border-radius: 12px;
  border: 1px solid var(--border);
  background: var(--bg);
  color: var(--fg);
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  outline: none;
  resize: vertical;
}

.field input:focus,
.field textarea:focus {
  border-color: var(--accent);
}

.btn {
  align-self: flex-start;
}

.logout {
  color: #e5484d;
}

.ok {
  margin: 0;
  color: #30a46c;
  font-size: 13px;
}

.error {
  margin: 0;
  color: #e5484d;
  font-size: 13px;
}
</style>
