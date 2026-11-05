<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DataBoard,
  Document,
  FolderOpened,
  CollectionTag,
  Files,
  ChatDotRound,
  Message,
  User,
  Lock,
  Upload,
  Tickets,
  Setting,
  Moon,
  Sunny,
  SwitchButton
} from '@element-plus/icons-vue'
import { useThemeStore } from '@/stores/theme'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const theme = useThemeStore()
const userStore = useUserStore()

const menu = computed(() => [
  { path: '/dashboard', title: '仪表盘', icon: DataBoard },
  { path: '/articles', title: '文章', icon: Document },
  { path: '/categories', title: '分类', icon: FolderOpened },
  { path: '/tags', title: '标签', icon: CollectionTag },
  { path: '/columns', title: '专栏', icon: Files },
  { path: '/comments', title: '评论', icon: ChatDotRound },
  { path: '/guestbook', title: '留言', icon: Message },
  { path: '/users', title: '用户', icon: User },
  { path: '/roles', title: '角色权限', icon: Lock },
  { path: '/resources', title: '资源', icon: Upload },
  { path: '/logs', title: '日志', icon: Tickets },
  { path: '/settings', title: '设置', icon: Setting }
])

const currentTitle = computed(() => String(route.meta.title ?? ''))

// 编辑页 /articles/12/edit 也要高亮「文章」菜单
const activeMenu = computed(() =>
  route.path.startsWith('/articles') ? '/articles' : route.path
)

async function logout() {
  await userStore.logout()
  router.replace('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="216px" class="aside">
      <div class="brand">
        <span class="brand__dot" />
        <span class="brand__text">Eason Blog</span>
        <span class="brand__badge">Admin</span>
      </div>

      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item v-for="item in menu" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header__left" />
        <div class="header__right">
          <el-tooltip :content="theme.dark ? '切换浅色' : '切换深色'">
            <el-button circle text @click="theme.toggle()">
              <el-icon><Moon v-if="!theme.dark" /><Sunny v-else /></el-icon>
            </el-button>
          </el-tooltip>

          <el-dropdown trigger="click">
            <span class="user">
              {{ userStore.user?.nickname || '管理员' }}
              <el-icon class="user__arrow"><SwitchButton /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item disabled>{{ userStore.user?.email }}</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main class="main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: var(--admin-card);
  border-right: 1px solid var(--admin-border);
  display: flex;
  flex-direction: column;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 18px;
  font-weight: 600;
  border-bottom: 1px solid var(--admin-border);
}

.brand__dot {
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #2f6bff;
}

.brand__text {
  font-size: 15px;
  letter-spacing: -0.01em;
}

.brand__badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: rgba(47, 107, 255, 0.12);
  color: #2f6bff;
  font-weight: 500;
}

.menu {
  border-right: none;
  padding: 8px;
}

.header {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--admin-card);
  border-bottom: 1px solid var(--admin-border);
}

.header__title {
  font-size: 15px;
  font-weight: 500;
}

.header__right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  cursor: pointer;
  color: var(--admin-text);
}

.user__arrow {
  font-size: 12px;
  color: var(--admin-muted);
}

.main {
  padding: 0;
  background: var(--admin-bg);
}
</style>
