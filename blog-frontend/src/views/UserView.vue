<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Bookmark, LogOut, Save } from 'lucide-vue-next'
import { authApi, interactionApi } from '@/api'
import type { ArticleSummary, NotificationItem } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { formatDate, formatDateTime } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

const tab = ref<'profile' | 'favorites' | 'notifications'>('profile')

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

const favorites = ref<ArticleSummary[]>([])
const favoriteTotal = ref(0)
const loadingFavorites = ref(false)

const notifications = ref<NotificationItem[]>([])
const loadingNotifications = ref(false)

async function loadFavorites() {
  loadingFavorites.value = true
  try {
    const page = await interactionApi.favorites(1, 20)
    favorites.value = page.records
    favoriteTotal.value = page.total
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingFavorites.value = false
  }
}

async function loadNotifications() {
  loadingNotifications.value = true
  try {
    notifications.value = await interactionApi.notifications(30)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingNotifications.value = false
  }
}

async function switchTab(next: 'profile' | 'favorites' | 'notifications') {
  tab.value = next
  if (next === 'favorites') await loadFavorites()
  if (next === 'notifications') await loadNotifications()
}

async function markRead(item: NotificationItem) {
  if (!item.read) {
    await interactionApi.markRead(item.id)
    item.read = true
  }
  if (item.link) router.push(item.link)
}

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

onMounted(async () => {
  await userStore.fetchMe()
  nickname.value = userStore.user?.nickname ?? ''
  bio.value = userStore.user?.bio ?? ''
})
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>个人中心</h1>
      <p class="muted" v-if="userStore.user">
        {{ userStore.user.email }} · 注册于 {{ formatDate(userStore.user.registerTime) }}
      </p>
    </header>

    <nav class="tabs">
      <button :class="{ active: tab === 'profile' }" @click="switchTab('profile')">资料</button>
      <button :class="{ active: tab === 'favorites' }" @click="switchTab('favorites')">
        <Bookmark :size="14" /> 收藏
      </button>
      <button :class="{ active: tab === 'notifications' }" @click="switchTab('notifications')">
        <Bell :size="14" /> 通知
      </button>
    </nav>

    <section v-if="tab === 'profile'">
      <div class="card block">
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
      </div>

      <div class="card block">
        <h2>安全</h2>
        <label class="field">
          <span>当前密码</span>
          <input v-model="oldPassword" type="password" autocomplete="current-password" />
        </label>
        <label class="field">
          <span>新密码</span>
          <input
            v-model="newPassword"
            type="password"
            autocomplete="new-password"
            placeholder="至少 8 位，含字母和数字"
          />
        </label>
        <p v-if="pwdMessage" class="ok">{{ pwdMessage }}</p>
        <p v-if="pwdError" class="error">{{ pwdError }}</p>
        <button class="btn" :disabled="changing" @click="changePassword">
          {{ changing ? '修改中…' : '修改密码' }}
        </button>
      </div>

      <button class="btn logout" @click="logout"><LogOut :size="15" /> 退出登录</button>
    </section>

    <section v-else-if="tab === 'favorites'" class="card block">
      <h2>我的收藏 {{ favoriteTotal ? `(${favoriteTotal})` : '' }}</h2>
      <p v-if="loadingFavorites" class="muted">加载中…</p>
      <div v-else-if="favorites.length" class="fav-list">
        <RouterLink v-for="item in favorites" :key="item.id" :to="`/article/${item.slug}`" class="fav">
          <span class="fav__title">{{ item.title }}</span>
          <span class="fav__meta muted">{{ formatDate(item.publishTime) }} · {{ item.viewCount }} 次阅读</span>
        </RouterLink>
      </div>
      <p v-else class="muted">还没有收藏文章，在文章页点「收藏」即可。</p>
    </section>

    <section v-else class="card block">
      <h2>通知</h2>
      <p v-if="loadingNotifications" class="muted">加载中…</p>
      <div v-else-if="notifications.length" class="notice-list">
        <button
          v-for="item in notifications"
          :key="item.id"
          class="notice"
          :class="{ 'notice--unread': !item.read }"
          @click="markRead(item)"
        >
          <span class="notice__head">
            <span class="notice__title">{{ item.title }}</span>
            <span class="notice__time muted">{{ formatDateTime(item.createTime) }}</span>
          </span>
          <span class="notice__body muted">{{ item.content }}</span>
        </button>
      </div>
      <p v-else class="muted">暂无通知。</p>
    </section>
  </div>
</template>

<style scoped>
.narrow {
  max-width: 760px;
}

.head {
  margin-bottom: 18px;
}

.head h1 {
  margin: 0 0 4px;
  font-size: 28px;
  letter-spacing: -0.02em;
}

.tabs {
  display: flex;
  gap: 6px;
  margin-bottom: 16px;
}

.tabs button {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: var(--card);
  color: var(--fg-soft);
  font-size: 13px;
  cursor: pointer;
}

.tabs button.active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
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

.fav-list {
  display: flex;
  flex-direction: column;
}

.fav {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--border);
}

.fav:last-child {
  border-bottom: none;
}

.fav__title {
  font-size: 14px;
}

.fav:hover .fav__title {
  color: var(--accent);
}

.fav__meta {
  font-size: 12px;
}

.notice-list {
  display: flex;
  flex-direction: column;
}

.notice {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 0;
  border: none;
  border-bottom: 1px dashed var(--border);
  background: none;
  text-align: left;
  cursor: pointer;
}

.notice:last-child {
  border-bottom: none;
}

.notice__head {
  display: flex;
  justify-content: space-between;
  gap: 10px;
}

.notice__title {
  font-size: 14px;
  color: var(--fg);
}

.notice--unread .notice__title {
  font-weight: 600;
}

.notice--unread .notice__title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--accent);
  vertical-align: middle;
}

.notice__time {
  font-size: 12px;
  white-space: nowrap;
}

.notice__body {
  font-size: 13px;
  line-height: 1.6;
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
