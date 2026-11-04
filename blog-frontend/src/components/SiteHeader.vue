<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Menu, X, PenLine, LogOut, Search, UserRound } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import { useNotificationStore } from '@/stores/notification'
import { useUserStore } from '@/stores/user'
import { adminBase } from '@/utils/admin'
import { openNotification } from '@/utils/notifications'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const notifStore = useNotificationStore()

const scrolled = ref(false)
const menuOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)

/** 右上角「新通知」轻提示：自动消失，不挡操作 */
const newNotification = ref<{ id: number; title: string; content: string } | null>(null)
let notifTimer: number | undefined

const unread = computed(() => notifStore.unread)

const navItems = [
  { label: '首页', to: '/', match: ['home'] },
  { label: '博客', to: '/blog', match: ['blog', 'article', 'search'] },
  { label: '分类', to: '/categories', match: ['categories', 'category'] },
  { label: '标签', to: '/tags', match: ['tags', 'tag'] },
  { label: '专栏', to: '/columns', match: ['columns', 'column'] },
  { label: '留言', to: '/guestbook', match: ['guestbook'] },
  { label: '关于', to: '/about', match: ['about'] }
]

/** 按路由名判断高亮，避免根路径 `/` 在任意子页面都被判为 active */
const currentName = computed(() => String(route.name ?? ''))
function isActive(item: { match: string[] }) {
  return item.match.includes(currentName.value)
}

const canWrite = computed(() => userStore.isAdmin)
const adminUrl = computed(() => adminBase())

function onScroll() {
  scrolled.value = window.scrollY > 8
}

function onDocumentClick(event: MouseEvent) {
  if (!userMenuOpen.value) return
  if (userMenuRef.value && !userMenuRef.value.contains(event.target as Node)) {
    userMenuOpen.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    userMenuOpen.value = false
    menuOpen.value = false
    newNotification.value = null
  }
}

function openLatest() {
  if (!notifStore.latest) return
  openNotification(notifStore.latest, router)
  newNotification.value = null
}

async function logout() {
  await userStore.logout()
  userMenuOpen.value = false
  notifStore.stopPolling()
  router.push('/')
}

watch(
  () => userStore.user?.id,
  (id) => {
    if (id) notifStore.startPolling()
    else notifStore.stopPolling()
  },
  { immediate: true }
)

// 轮询发现新通知时弹一个小卡片，6 秒自动消失
watch(
  () => notifStore.latestSeq,
  () => {
    if (!notifStore.latest) return
    newNotification.value = {
      id: notifStore.latest.id,
      title: notifStore.latest.title,
      content: notifStore.latest.content ?? ''
    }
    window.clearTimeout(notifTimer)
    notifTimer = window.setTimeout(() => (newNotification.value = null), 6000)
  }
)

watch(
  () => route.fullPath,
  () => {
    userMenuOpen.value = false
    menuOpen.value = false
    if (userStore.isLoggedIn) notifStore.fetchUnread()
  }
)

onMounted(() => {
  onScroll()
  if (userStore.isLoggedIn) notifStore.startPolling()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
  window.clearTimeout(notifTimer)
  notifStore.stopPolling()
})
</script>

<template>
  <header class="nav" :class="{ 'nav--scrolled': scrolled }">
    <div class="nav__inner container">
      <RouterLink to="/" class="brand">
        <span class="brand__dot" />
        <span class="brand__text">Eason Blog</span>
      </RouterLink>

      <nav class="links" :class="{ 'links--open': menuOpen }">
        <RouterLink
          v-for="item in navItems"
          :key="item.to"
          :to="item.to"
          class="link"
          :class="{ 'link--active': isActive(item) }"
          @click="menuOpen = false"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <RouterLink to="/search" class="icon-btn" title="搜索" aria-label="搜索">
          <Search :size="18" />
        </RouterLink>

        <ThemeToggle />

        <div v-if="userStore.isLoggedIn" ref="userMenuRef" class="user">
          <button
            class="btn btn--ghost"
            :aria-expanded="userMenuOpen"
            aria-haspopup="menu"
            @click.stop="userMenuOpen = !userMenuOpen"
          >
            <img v-if="userStore.user?.avatar" :src="userStore.user.avatar" class="avatar" alt="" />
            <UserRound v-else :size="16" />
            <span class="user__name">{{ userStore.user?.nickname }}</span>
            <span v-if="unread > 0" class="badge">{{ unread > 99 ? '99+' : unread }}</span>
          </button>

          <div v-if="userMenuOpen" class="user__menu-wrap" @click.stop>
            <div class="user__menu card" role="menu">
              <RouterLink to="/user" class="user__item" role="menuitem" @click="userMenuOpen = false">
                <UserRound :size="15" /> 个人中心
              </RouterLink>
              <RouterLink to="/user?tab=notifications" class="user__item" role="menuitem">
                <Bell :size="15" /> 通知
                <span v-if="unread > 0" class="badge badge--inline">{{ unread }}</span>
              </RouterLink>
              <a
                v-if="canWrite"
                class="user__item"
                role="menuitem"
                :href="adminUrl"
                target="_blank"
                rel="noopener"
                @click="userMenuOpen = false"
              >
                <PenLine :size="15" /> 写文章
              </a>
              <button class="user__item user__item--danger" role="menuitem" @click="logout">
                <LogOut :size="15" /> 退出登录
              </button>
            </div>
          </div>
        </div>

        <RouterLink v-else to="/login" class="btn btn--ghost">
          <UserRound :size="16" /> 登录
        </RouterLink>

        <button class="menu-btn" aria-label="菜单" @click="menuOpen = !menuOpen">
          <X v-if="menuOpen" :size="20" />
          <Menu v-else :size="20" />
        </button>
      </div>
    </div>
  </header>

  <!-- 新通知轻提示：右上角、自动消失、点击直达 -->
  <Transition name="notif-pop">
    <div v-if="newNotification" class="notif-toast" role="status" @click="openLatest">
      <Bell :size="16" class="notif-toast__icon" />
      <div class="notif-toast__body">
        <p class="notif-toast__title">{{ newNotification.title }}</p>
        <p class="notif-toast__content">{{ newNotification.content }}</p>
      </div>
      <button class="notif-toast__close" aria-label="关闭" @click.stop="newNotification = null">×</button>
    </div>
  </Transition>
</template>

<style scoped>
.notif-toast {
  position: fixed;
  top: 76px;
  right: 24px;
  z-index: 120;
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 320px;
  max-width: calc(100vw - 32px);
  padding: 12px 14px;
  border: 1px solid var(--border);
  border-radius: 14px;
  background: var(--nav-bg);
  backdrop-filter: blur(14px);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.16);
  cursor: pointer;
  transition: transform 0.15s ease, border-color 0.15s ease;
}

.notif-toast:hover {
  transform: translateY(-2px);
  border-color: var(--accent);
}

.notif-toast__icon {
  color: var(--accent);
  margin-top: 2px;
  flex-shrink: 0;
}

.notif-toast__body {
  min-width: 0;
  flex: 1;
}

.notif-toast__title {
  margin: 0 0 2px;
  font-size: 13px;
  font-weight: 600;
}

.notif-toast__content {
  margin: 0;
  font-size: 12px;
  color: var(--fg-soft);
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.notif-toast__close {
  border: none;
  background: none;
  color: var(--muted);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  padding: 0 2px;
}

.notif-pop-enter-active,
.notif-pop-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.notif-pop-enter-from,
.notif-pop-leave-to {
  opacity: 0;
  transform: translateX(12px);
}
.nav {
  position: fixed;
  inset: 0 0 auto 0;
  z-index: 50;
  background: transparent;
  transition: background 0.25s ease, border-color 0.25s ease, backdrop-filter 0.25s ease;
  border-bottom: 1px solid transparent;
}

.nav--scrolled {
  background: var(--nav-bg);
  backdrop-filter: saturate(180%) blur(18px);
  -webkit-backdrop-filter: saturate(180%) blur(18px);
  border-bottom-color: var(--border);
}

.nav__inner {
  height: 64px;
  display: flex;
  align-items: center;
  gap: 24px;
}

.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  letter-spacing: -0.02em;
  flex-shrink: 0;
}

.brand__dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 4px var(--accent-soft);
}

.links {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-left: 8px;
}

.link {
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 14px;
  color: var(--fg-soft);
  transition: color 0.15s ease, background 0.15s ease;
  white-space: nowrap;
}

.link:hover {
  color: var(--fg);
  background: var(--bg-soft);
}

.link--active {
  color: var(--fg);
  background: var(--bg-soft);
  font-weight: 500;
}

.actions {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border);
  color: var(--fg-soft);
  transition: color 0.15s ease, background 0.15s ease;
}

.icon-btn:hover {
  color: var(--fg);
  background: var(--bg-soft);
}

.btn--ghost {
  background: transparent;
  padding: 6px 12px;
  gap: 6px;
}

.user {
  position: relative;
}

.avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  object-fit: cover;
}

.user__name {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.badge {
  min-width: 17px;
  height: 17px;
  padding: 0 5px;
  border-radius: 999px;
  background: #e5484d;
  color: #fff;
  font-size: 11px;
  line-height: 17px;
  text-align: center;
}

.badge--inline {
  margin-left: auto;
}

.user__menu-wrap {
  position: absolute;
  right: 0;
  top: 100%;
  padding-top: 8px;
  z-index: 60;
}

.user__menu {
  min-width: 190px;
  padding: 6px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 10px;
  font-size: 14px;
  background: none;
  border: none;
  color: var(--fg);
  cursor: pointer;
  text-align: left;
  width: 100%;
  white-space: nowrap;
}

.user__item:hover {
  background: var(--bg-soft);
}

.user__item--danger {
  color: #e5484d;
}

.menu-btn {
  display: none;
  background: none;
  border: none;
  color: var(--fg);
  cursor: pointer;
  padding: 6px;
}

@media (max-width: 980px) {
  .menu-btn {
    display: inline-flex;
  }

  .links {
    position: absolute;
    top: 64px;
    left: 0;
    right: 0;
    display: none;
    flex-direction: column;
    align-items: stretch;
    padding: 12px 24px 20px;
    background: var(--nav-bg);
    backdrop-filter: blur(18px);
    border-bottom: 1px solid var(--border);
    margin: 0;
  }

  .links--open {
    display: flex;
  }

  .link {
    padding: 10px 8px;
  }

  .user__name {
    display: none;
  }
}
</style>
