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
          component: () => import('@/views/ArticlesView.vue'),
          meta: { title: '文章管理' }
        },
        {
          path: 'articles/create',
          name: 'article-create',
          component: () => import('@/views/ArticleEditView.vue'),
          meta: { title: '新建文章' }
        },
        {
          path: 'articles/:id/edit',
          name: 'article-edit',
          component: () => import('@/views/ArticleEditView.vue'),
          meta: { title: '编辑文章' }
        },
        {
          path: 'categories',
          name: 'categories',
          component: () => import('@/views/CategoriesView.vue'),
          meta: { title: '分类管理' }
        },
        {
          path: 'tags',
          name: 'tags',
          component: () => import('@/views/TagsView.vue'),
          meta: { title: '标签管理' }
        },
        {
          path: 'columns',
          name: 'columns',
          component: () => import('@/views/ColumnsView.vue'),
          meta: { title: '专栏管理' }
        },
        {
          path: 'comments',
          name: 'comments',
          component: () => import('@/views/ModerationView.vue'),
          meta: { title: '评论审核' }
        },
        {
          path: 'guestbook',
          name: 'guestbook',
          component: () => import('@/views/ModerationView.vue'),
          meta: { title: '留言管理' }
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UsersView.vue'),
          meta: { title: '用户管理' }
        },
        {
          path: 'roles',
          name: 'roles',
          component: () => import('@/views/RolesView.vue'),
          meta: { title: '角色权限' }
        },
        {
          path: 'resources',
          name: 'resources',
          component: () => import('@/views/ResourcesView.vue'),
          meta: { title: '资源管理' }
        },
        {
          path: 'logs',
          name: 'logs',
          component: () => import('@/views/LogsView.vue'),
          meta: { title: '日志审计' }
        },
        {
          path: 'settings',
          name: 'settings',
          component: () => import('@/views/SettingsView.vue'),
          meta: { title: '系统设置' }
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
