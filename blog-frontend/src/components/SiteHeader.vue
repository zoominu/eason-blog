<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Menu, X, PenLine, LogOut, UserRound } from 'lucide-vue-next'
import ThemeToggle from './ThemeToggle.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

const scrolled = ref(false)
const menuOpen = ref(false)
const userMenuOpen = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)

const navItems = [
  { label: '首页', to: '/' },
  { label: '博客', to: '/blog' },
  { label: '分类', to: '/categories' },
  { label: '标签', to: '/tags' },
  { label: '专栏', to: '/columns' },
  { label: '留言', to: '/guestbook' },
  { label: '关于', to: '/about' }
]

const canWrite = computed(() => userStore.isAdmin)

// 后台默认跑在 5174；用当前访问的主机名拼地址，
// 这样从虚拟机/局域网打开前台时，"写文章"不会错误地跳到 127.0.0.1。
const adminUrl = computed(
  () => `${window.location.protocol}//${window.location.hostname}:5174`
)

function onScroll() {
  scrolled.value = window.scrollY > 8
}

/**
 * 点击空白处关闭用户菜单。
 *
 * 之前用 @mouseleave 关闭，鼠标从按钮移到菜单要跨过 8px 间隙，
 * 会先触发 leave 导致菜单瞬间消失，根本点不到。改用 click-outside 后
 * 菜单只在「再次点击按钮 / 点击其它位置 / 按 Esc」时关闭。
 */
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

async function logout() {
  await userStore.logout()
  userMenuOpen.value = false
  router.push('/')
}

onMounted(() => {
  onScroll()
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
          active-class="link--active"
          @click="menuOpen = false"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <ThemeToggle />

        <div v-if="userStore.isLoggedIn" ref="userMenuRef" class="user">
          <button
            class="btn btn--ghost"
            :aria-expanded="userMenuOpen"
            aria-haspopup="menu"
            @click.stop="userMenuOpen = !userMenuOpen"
          >
            <UserRound :size="16" />
            <span class="user__name">{{ userStore.user?.nickname }}</span>
          </button>

          <div v-if="userMenuOpen" class="user__menu-wrap" @click.stop>
            <div class="user__menu card" role="menu">
              <RouterLink to="/user" class="user__item" role="menuitem" @click="userMenuOpen = false">
                <UserRound :size="15" /> 个人中心
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

.btn--ghost {
  background: transparent;
  padding: 6px 12px;
  gap: 6px;
}

.user {
  position: relative;
}

.user__name {
  max-width: 90px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 外层负责 8px 的「安全距离」，内层才是带背景的卡片，
   这样鼠标从按钮移到菜单不会经过空隙，也就不会误触关闭逻辑 */
.user__menu-wrap {
  position: absolute;
  right: 0;
  top: 100%;
  padding-top: 8px;
  z-index: 60;
}

.user__menu {
  min-width: 176px;
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
