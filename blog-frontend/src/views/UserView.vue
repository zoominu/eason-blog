<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Bookmark, Camera, Heart, LogOut, MessageSquare, Save, Trash2 } from 'lucide-vue-next'
import { authApi, interactionApi } from '@/api'
import type { ArticleSummary, MyComment, NotificationItem } from '@/api/types'
import Pagination from '@/components/Pagination.vue'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import { formatDate, formatDateTime } from '@/utils/format'
import { openNotification as openNotificationLink } from '@/utils/notifications'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notifStore = useNotificationStore()

type Tab = 'profile' | 'favorites' | 'likes' | 'comments' | 'notifications'
const tab = ref<Tab>('profile')

const nickname = ref('')
const bio = ref('')
const saving = ref(false)
const message = ref('')
const error = ref('')

const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)
const avatarMessage = ref('')

async function onAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    avatarMessage.value = '头像不能超过 2MB'
    return
  }
  uploadingAvatar.value = true
  avatarMessage.value = ''
  try {
    const result = await authApi.uploadAvatar(file)
    if (userStore.user) userStore.user.avatar = result.url
    avatarMessage.value = '头像已更新'
  } catch (e) {
    avatarMessage.value = (e as Error).message
  } finally {
    uploadingAvatar.value = false
  }
}

const oldPassword = ref('')
const newPassword = ref('')
const changing = ref(false)
const pwdMessage = ref('')
const pwdError = ref('')

const favorites = ref<ArticleSummary[]>([])
const favoriteTotal = ref(0)
const favoritePage = ref(1)
const favoritePages = ref(0)
const loadingFavorites = ref(false)
const favoriteSelected = ref<number[]>([])
const favoriteBusy = ref(false)

const likes = ref<ArticleSummary[]>([])
const likeTotal = ref(0)
const likePage = ref(1)
const likePages = ref(0)
const loadingLikes = ref(false)
const likeSelected = ref<number[]>([])
const likeBusy = ref(false)

const myComments = ref<MyComment[]>([])
const myCommentTotal = ref(0)
const myCommentPage = ref(1)
const myCommentPages = ref(0)
const loadingMyComments = ref(false)
const commentSelected = ref<number[]>([])
const commentBusy = ref(false)

const notifications = ref<NotificationItem[]>([])
const notificationLimit = ref(20)
const loadingNotifications = ref(false)
const notifType = ref('')

const NOTIF_TYPES = [
  { value: '', label: '全部' },
  { value: 'COMMENT', label: '评论' },
  { value: 'REPLY', label: '回复' },
  { value: 'SYSTEM', label: '系统' }
]

const STATUS_TEXT: Record<number, string> = { 0: '待审核', 1: '已通过', 2: '已拒绝' }

async function loadFavorites(pageNo = 1) {
  loadingFavorites.value = true
  try {
    const page = await interactionApi.favorites(pageNo, 10)
    favorites.value = page.records
    favoriteTotal.value = page.total
    favoritePage.value = page.current
    favoritePages.value = page.pages
    favoriteSelected.value = []
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingFavorites.value = false
  }
}

async function loadLikes(pageNo = 1) {
  loadingLikes.value = true
  try {
    const page = await interactionApi.likes(pageNo, 10)
    likes.value = page.records
    likeTotal.value = page.total
    likePage.value = page.current
    likePages.value = page.pages
    likeSelected.value = []
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingLikes.value = false
  }
}

async function loadMyComments(pageNo = 1) {
  loadingMyComments.value = true
  try {
    const page = await interactionApi.myComments(pageNo, 10)
    myComments.value = page.records
    myCommentTotal.value = page.total
    myCommentPage.value = page.current
    myCommentPages.value = page.pages
    commentSelected.value = []
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingMyComments.value = false
  }
}

async function loadNotifications(limit = notificationLimit.value) {
  loadingNotifications.value = true
  try {
    notificationLimit.value = limit
    notifications.value = await interactionApi.notifications(limit, notifType.value || undefined)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    loadingNotifications.value = false
  }
}

async function removeSelectedFavorites() {
  if (!favoriteSelected.value.length) return
  favoriteBusy.value = true
  try {
    const result = await interactionApi.removeFavorites(favoriteSelected.value)
    message.value = `已取消收藏 ${result.removed} 篇`
    await loadFavorites(favoritePage.value)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    favoriteBusy.value = false
  }
}

async function removeSelectedLikes() {
  if (!likeSelected.value.length) return
  likeBusy.value = true
  try {
    const result = await interactionApi.removeLikes(likeSelected.value)
    message.value = `已取消点赞 ${result.removed} 篇`
    await loadLikes(likePage.value)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    likeBusy.value = false
  }
}

async function deleteSelectedComments() {
  if (!commentSelected.value.length) return
  commentBusy.value = true
  try {
    const result = await interactionApi.deleteMyComments(commentSelected.value)
    message.value = result.deleted === commentSelected.value.length
      ? `已删除 ${result.deleted} 条评论`
      : `已删除 ${result.deleted} 条（超过 3 分钟或有回复的已跳过）`
    await loadMyComments(myCommentPage.value)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    commentBusy.value = false
  }
}

async function deleteOneComment(id: number) {
  commentBusy.value = true
  try {
    await interactionApi.deleteMyComment(id)
    message.value = '评论已删除'
    await loadMyComments(myCommentPage.value)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    commentBusy.value = false
  }
}

async function switchTab(next: Tab) {
  tab.value = next
  // 标签页写进 URL，刷新/分享后仍停在对应页
  router.replace({ path: '/user', query: next === 'profile' ? {} : { tab: next } })
  if (next === 'favorites') await loadFavorites(1)
  if (next === 'likes') await loadLikes(1)
  if (next === 'comments') await loadMyComments(1)
  if (next === 'notifications') await loadNotifications(20)
}

async function markRead(item: NotificationItem) {
  if (!item.read) {
    await interactionApi.markRead(item.id)
    item.read = true
    notifStore.fetchUnread()
  }
}

/**
 * 打开通知：
 * - 审核类通知（/admin/...）属于后台，前台跳到独立后台地址；
 * - 其余按站内路径跳转，文章通知会带 ?comment= 自动定位高亮。
 */
function openNotification(item: NotificationItem) {
  markRead(item)
  openNotificationLink(item, router)
}

const notifBusy = ref(false)
const notifHint = ref('')

async function markAllRead() {
  notifBusy.value = true
  try {
    const updated = await notifStore.markAllRead()
    notifHint.value = `已标记 ${updated} 条为已读`
    await loadNotifications()
  } catch (e) {
    notifHint.value = (e as Error).message
  } finally {
    notifBusy.value = false
  }
}

async function deleteReadNotifications() {
  notifBusy.value = true
  try {
    const deleted = await notifStore.deleteRead()
    notifHint.value = `已删除 ${deleted} 条已读通知`
    await loadNotifications()
  } catch (e) {
    notifHint.value = (e as Error).message
  } finally {
    notifBusy.value = false
  }
}

// 通知实时同步：轮询发现新通知时，如果正停在通知页就直接刷新列表
watch(
  () => notifStore.latestSeq,
  () => {
    if (tab.value === 'notifications') loadNotifications()
  }
)

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
    // 改密后后端会递增 token 版本，旧 Cookie 已失效，这里直接退出避免“还在登录态”的错觉
    pwdMessage.value = '密码已修改，请重新登录'
    oldPassword.value = ''
    newPassword.value = ''
    await userStore.logout()
    setTimeout(() => router.replace('/login'), 800)
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

const verifyHint = ref('')
const sendingVerify = ref(false)

async function resendVerification() {
  sendingVerify.value = true
  verifyHint.value = ''
  try {
    await authApi.resendVerification()
    verifyHint.value = '验证邮件已发送（开发环境请看后端日志）'
  } catch (e) {
    verifyHint.value = (e as Error).message
  } finally {
    sendingVerify.value = false
  }
}

onMounted(async () => {
  await userStore.fetchMe()
  nickname.value = userStore.user?.nickname ?? ''
  bio.value = userStore.user?.bio ?? ''

  // 支持从页头「通知」直接跳 /user?tab=notifications
  const requested = String(route.query.tab ?? '')
  const allowed: Tab[] = ['notifications', 'favorites', 'likes', 'comments']
  if (allowed.includes(requested as Tab)) {
    await switchTab(requested as Tab)
  }
})

watch(
  () => route.query.tab,
  (value) => {
    const allowed: Tab[] = ['notifications', 'favorites', 'likes', 'comments']
    if (allowed.includes(value as Tab)) {
      tab.value = value as Tab
      if (value === 'favorites') loadFavorites(1)
      else if (value === 'likes') loadLikes(1)
      else if (value === 'comments') loadMyComments(1)
      else loadNotifications(20)
    }
  }
)
</script>

<template>
  <div class="container narrow">
    <header class="head">
      <h1>个人中心</h1>
      <p class="muted" v-if="userStore.user">
        {{ userStore.user.email }} · 注册于 {{ formatDate(userStore.user.registerTime) }}
      </p>
      <p v-if="userStore.user" class="verify">
        <template v-if="userStore.user.emailVerified">
          <span class="ok">邮箱已验证</span>
        </template>
        <template v-else>
          <span class="warn">邮箱未验证</span>
          <button class="link-btn" :disabled="sendingVerify" @click="resendVerification">
            {{ sendingVerify ? '发送中…' : '发送验证邮件' }}
          </button>
        </template>
        <span v-if="verifyHint" class="muted hint">{{ verifyHint }}</span>
      </p>
    </header>

    <nav class="tabs">
      <button :class="{ active: tab === 'profile' }" @click="switchTab('profile')">资料</button>
      <button :class="{ active: tab === 'favorites' }" @click="switchTab('favorites')">
        <Bookmark :size="14" /> 收藏
      </button>
      <button :class="{ active: tab === 'likes' }" @click="switchTab('likes')">
        <Heart :size="14" /> 点赞
      </button>
      <button :class="{ active: tab === 'comments' }" @click="switchTab('comments')">
        <MessageSquare :size="14" /> 评论
      </button>
      <button :class="{ active: tab === 'notifications' }" @click="switchTab('notifications')">
        <Bell :size="14" /> 通知
      </button>
    </nav>

    <!-- 各标签页共用的操作反馈 -->
    <p v-if="message" class="ok page-message">{{ message }}</p>
    <p v-if="error" class="error page-message">{{ error }}</p>

    <section v-if="tab === 'profile'">
      <div class="card block">
        <h2>资料</h2>
        <div class="avatar-row">
          <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" alt="头像" class="avatar" />
          <div v-else class="avatar avatar--empty">{{ (userStore.user?.nickname ?? '?').slice(0, 1) }}</div>
          <div class="avatar-actions">
            <button class="btn btn-sm" :disabled="uploadingAvatar" @click="avatarInput?.click()">
              <Camera :size="14" /> {{ uploadingAvatar ? '上传中…' : '上传头像' }}
            </button>
            <span class="muted">支持 png/jpg/webp，最大 2MB</span>
            <span v-if="avatarMessage" class="muted">{{ avatarMessage }}</span>
            <input
              ref="avatarInput"
              type="file"
              accept="image/png,image/jpeg,image/gif,image/webp"
              class="hidden-input"
              @change="onAvatarChange"
            />
          </div>
        </div>
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
      <div class="list-head">
        <h2>我的收藏 {{ favoriteTotal ? `(${favoriteTotal})` : '' }}</h2>
        <button class="btn btn-sm" :disabled="!favoriteSelected.length || favoriteBusy" @click="removeSelectedFavorites">
          <Trash2 :size="13" /> 取消收藏{{ favoriteSelected.length ? ` (${favoriteSelected.length})` : '' }}
        </button>
      </div>
      <p v-if="loadingFavorites" class="muted">加载中…</p>
      <div v-else-if="favorites.length" class="article-list">
        <label v-for="item in favorites" :key="item.id" class="article-row">
          <input v-model="favoriteSelected" type="checkbox" :value="item.id" />
          <RouterLink :to="`/article/${item.slug}`" class="article-row__link">
            <span class="article-row__title">{{ item.title }}</span>
            <span class="article-row__meta muted">{{ formatDate(item.publishTime) }} · {{ item.viewCount }} 次阅读</span>
          </RouterLink>
        </label>
        <Pagination :current="favoritePage" :pages="favoritePages" @change="loadFavorites" />
      </div>
      <p v-else class="muted">还没有收藏文章，在文章页点「收藏」即可。</p>
    </section>

    <section v-else-if="tab === 'likes'" class="card block">
      <div class="list-head">
        <h2>我点赞的文章 {{ likeTotal ? `(${likeTotal})` : '' }}</h2>
        <button class="btn btn-sm" :disabled="!likeSelected.length || likeBusy" @click="removeSelectedLikes">
          <Trash2 :size="13" /> 取消点赞{{ likeSelected.length ? ` (${likeSelected.length})` : '' }}
        </button>
      </div>
      <p v-if="loadingLikes" class="muted">加载中…</p>
      <div v-else-if="likes.length" class="article-list">
        <label v-for="item in likes" :key="item.id" class="article-row">
          <input v-model="likeSelected" type="checkbox" :value="item.id" />
          <RouterLink :to="`/article/${item.slug}`" class="article-row__link">
            <span class="article-row__title">{{ item.title }}</span>
            <span class="article-row__meta muted">{{ formatDate(item.publishTime) }} · {{ item.viewCount }} 次阅读</span>
          </RouterLink>
        </label>
        <Pagination :current="likePage" :pages="likePages" @change="loadLikes" />
      </div>
      <p v-else class="muted">还没有点赞的文章。</p>
    </section>

    <section v-else-if="tab === 'comments'" class="card block">
      <div class="list-head">
        <h2>我的评论 {{ myCommentTotal ? `(${myCommentTotal})` : '' }}</h2>
        <button class="btn btn-sm" :disabled="!commentSelected.length || commentBusy" @click="deleteSelectedComments">
          <Trash2 :size="13" /> 删除评论{{ commentSelected.length ? ` (${commentSelected.length})` : '' }}
        </button>
      </div>
      <p class="muted tip">发布 3 分钟内且没有回复的评论可以删除。</p>
      <p v-if="loadingMyComments" class="muted">加载中…</p>
      <div v-else-if="myComments.length" class="comment-list">
        <div v-for="item in myComments" :key="item.id" class="comment-row">
          <input v-model="commentSelected" type="checkbox" :value="item.id" />
          <div class="comment-row__body">
            <RouterLink
              v-if="item.articleSlug"
              :to="`/article/${item.articleSlug}`"
              class="comment-row__article"
            >
              {{ item.articleTitle }}
            </RouterLink>
            <span v-else class="comment-row__article">{{ item.articleTitle }}</span>
            <p class="comment-row__text">{{ item.content }}</p>
            <div class="comment-row__meta muted">
              <span>{{ formatDateTime(item.createTime) }}</span>
              <span class="tag">{{ STATUS_TEXT[item.status] ?? '未知' }}</span>
              <button
                v-if="item.canDelete"
                class="link-btn"
                :disabled="commentBusy"
                @click="deleteOneComment(item.id)"
              >
                删除
              </button>
            </div>
          </div>
        </div>
        <Pagination :current="myCommentPage" :pages="myCommentPages" @change="loadMyComments" />
      </div>
      <p v-else class="muted">还没有发表过评论。</p>
    </section>

    <section v-else class="card block">
      <div class="notif-head">
        <h2>通知</h2>
        <div class="notif-actions">
          <button class="btn btn-sm" :disabled="notifBusy" @click="markAllRead">一键已读</button>
          <button class="btn btn-sm" :disabled="notifBusy" @click="deleteReadNotifications">删除已读</button>
        </div>
      </div>
      <div class="notif-filter">
        <button
          v-for="type in NOTIF_TYPES"
          :key="type.value"
          class="chip"
          :class="{ 'chip--active': notifType === type.value }"
          @click="notifType = type.value; loadNotifications(20)"
        >
          {{ type.label }}
        </button>
      </div>
      <p v-if="notifHint" class="muted notif-hint">{{ notifHint }}</p>
      <p v-if="loadingNotifications" class="muted">加载中…</p>
      <div v-else-if="notifications.length" class="notice-list">
        <button
          v-for="item in notifications"
          :key="item.id"
          class="notice"
          :class="{ 'notice--unread': !item.read }"
          @click="openNotification(item)"
        >
          <span class="notice__head">
            <span class="notice__title">{{ item.title }}</span>
            <span class="notice__time muted">{{ formatDateTime(item.createTime) }}</span>
          </span>
          <span class="notice__body muted">{{ item.content }}</span>
        </button>
        <button
          v-if="notifications.length >= notificationLimit"
          class="btn more"
          @click="loadNotifications(notificationLimit + 20)"
        >
          加载更多
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

.notif-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.notif-head h2 {
  margin: 0;
}

.notif-actions {
  display: flex;
  gap: 8px;
}

.btn-sm {
  padding: 5px 12px;
  font-size: 12px;
}

.notif-hint {
  font-size: 12px;
  margin: 0 0 10px;
}

.ok {
  margin: 0;
  color: #30a46c;
  font-size: 13px;
}

.verify {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 6px 0 0;
  font-size: 13px;
}

.warn {
  color: #d97706;
  font-size: 13px;
}

.hint {
  font-size: 12px;
}

.link-btn {
  border: none;
  background: none;
  padding: 0;
  color: var(--accent);
  font-size: 13px;
  cursor: pointer;
}

.error {
  margin: 0;
  color: #e5484d;
  font-size: 13px;
}

.page-message {
  margin: -6px 0 12px;
  font-size: 13px;
}

.avatar-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 4px 0 8px;
}

.avatar {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  object-fit: cover;
  border: 1px solid var(--border);
  background: var(--bg-soft);
}

.avatar--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  color: var(--muted);
}

.avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
}

.hidden-input {
  display: none;
}

.list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.list-head h2 {
  margin: 0;
}

.article-list,
.comment-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.article-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 4px;
  border-bottom: 1px solid var(--border);
}

.article-row__link {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  color: var(--fg);
  text-decoration: none;
}

.article-row__title {
  font-size: 14px;
  line-height: 1.5;
  overflow-wrap: anywhere;
}

.article-row__meta {
  font-size: 12px;
}

.comment-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 4px;
  border-bottom: 1px solid var(--border);
}

.comment-row__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  flex: 1;
}

.comment-row__article {
  font-size: 13px;
  color: var(--accent);
  text-decoration: none;
}

.comment-row__text {
  margin: 0;
  font-size: 14px;
  color: var(--fg);
  line-height: 1.6;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  word-break: break-word;
}

.comment-row__meta {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 12px;
}

.comment-row__meta .tag {
  padding: 1px 7px;
  border-radius: 999px;
  border: 1px solid var(--border);
  font-size: 11px;
}

.tip {
  margin: 0;
  font-size: 12px;
}

.notif-filter {
  display: flex;
  gap: 6px;
  margin: 4px 0 10px;
}

.chip {
  padding: 4px 12px;
  border-radius: 999px;
  border: 1px solid var(--border);
  background: transparent;
  color: var(--fg-soft);
  font-size: 12px;
  cursor: pointer;
}

.chip--active {
  background: var(--accent);
  border-color: var(--accent);
  color: #fff;
}
</style>
