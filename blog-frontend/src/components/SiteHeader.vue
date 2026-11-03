<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Bell, Menu, X, PenLine, LogOut, Search, UserRound } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import { interactionApi } from '@/api'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const scrolled = ref(false)
const menuOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
const unread = ref(0)

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

/**
 * 后台地址：生产由 VITE_ADMIN_URL 指向 /admin/（同域反代），
 * 开发默认 5174；不再硬编码 host:5174，避免线上点「写文章」连不上。
 */
const adminUrl = computed(() => {
  const configured = import.meta.env.VITE_ADMIN_URL
  if (configured) return configured
  return `${window.location.protocol}//${window.location.hostname}:5174`
})

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
  }
}

async function refreshUnread() {
  if (!userStore.isLoggedIn) {
    unread.value = 0
    return
  }
  try {
    const result = await interactionApi.unreadCount()
    unread.value = result.count
  } catch {
    unread.value = 0
  }
}

async function logout() {
  await userStore.logout()
  userMenuOpen.value = false
  unread.value = 0
  router.push('/')
}

watch(
  () => userStore.user?.id,
  () => refreshUnread()
)

watch(
  () => route.fullPath,
  () => {
    userMenuOpen.value = false
    menuOpen.value = false
    refreshUnread()
  }
)

onMounted(() => {
  onScroll()
  refreshUnread()
  window.addEventListener('scroll', onScroll, { passive: true })
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onKeydown)
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onKeydown)
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
</template>

<style scoped>
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
