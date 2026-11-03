import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'
import { useUserStore } from '@/stores/user'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true }
    },
    {
      path: '/',
      component: AdminLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '仪表盘' }
        },
        {
          path: 'articles',
          name: 'articles',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '文章管理', note: '文章 CRUD、Markdown 编辑、发布/下线将在 P2 实现。' }
        },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '分类管理', note: '分类树维护将在 P2 实现。' }
        },
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '标签管理', note: '标签维护将在 P2 实现。' }
        },
        {
          path: 'columns',
          name: 'columns',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '专栏管理', note: '专栏与文章排序将在 P2 实现。' }
        },
        {
          path: 'comments',
          name: 'comments',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '评论审核', note: '评论审核与回复将在 P4 实现。' }
        },
        {
          path: 'guestbook',
          name: 'guestbook',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '留言管理', note: '留言审核与回复将在 P4 实现。' }
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '用户管理', note: '用户禁用/启用将在 P5 实现。' }
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '角色权限', note: 'RBAC 配置界面将在 P5 实现。' }
        },
        {
          path: 'resources',
          name: 'resources',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '资源管理', note: '上传、预览与引用检查将在 P5 实现。' }
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '日志审计', note: '登录日志与操作日志将在 P5 实现。' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/PlaceholderView.vue'),
          meta: { title: '系统设置', note: '站点配置与管理员资料将在 P5 实现。' }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
  ]
})

// 前端守卫只做跳转体验；所有 /api/v1/admin/** 的权限由后端 SecurityConfig 校验
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  const userStore = useUserStore()
  const user = await userStore.fetchMe()
  if (!user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
