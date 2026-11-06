/**
 * 前台路由表。
 *
 * <p>除 404 外全部挂在 `MainLayout` 下，共享导航与页脚；认证页通过
 * `MainLayout` 判断路由名切换全屏壁纸。`requiresAuth` 的路由会在 beforeEach
 * 里重新请求 `/auth/me`，避免只依赖前端内存判断登录态。</p>
 */
import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'
import { useUserStore } from '@/stores/user'
import { resetSeo } from '@/utils/seo'

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
  routes: [
    {
      path: '/',
      component: MainLayout,
      children: [
        { path: '', name: 'home', component: () => import('@/views/HomeView.vue') },
        { path: 'blog', name: 'blog', component: () => import('@/views/BlogView.vue') },
        { path: 'article/:slug', name: 'article', component: () => import('@/views/ArticleView.vue') },
        { path: 'categories', name: 'categories', component: () => import('@/views/CategoriesView.vue') },
        {
          path: 'category/:slug',
          name: 'category',
          component: () => import('@/views/CategoryDetailView.vue')
        },
        { path: 'tags', name: 'tags', component: () => import('@/views/TagsView.vue') },
        { path: 'tag/:slug', name: 'tag', component: () => import('@/views/TagDetailView.vue') },
        { path: 'columns', name: 'columns', component: () => import('@/views/ColumnsView.vue') },
        { path: 'column/:slug', name: 'column', component: () => import('@/views/ColumnsView.vue') },
        { path: 'search', name: 'search', component: () => import('@/views/SearchView.vue') },
        { path: 'guestbook', name: 'guestbook', component: () => import('@/views/GuestbookView.vue') },
        { path: 'about', name: 'about', component: () => import('@/views/AboutView.vue') },
        { path: 'terms', name: 'terms', component: () => import('@/views/TermsView.vue') },
        { path: 'privacy', name: 'privacy', component: () => import('@/views/PrivacyView.vue') },
        { path: 'login', name: 'login', component: () => import('@/views/LoginView.vue') },
        { path: 'register', name: 'register', component: () => import('@/views/RegisterView.vue') },
        {
          path: 'forgot-password',
          name: 'forgot-password',
          component: () => import('@/views/ForgotPasswordView.vue')
        },
        {
          path: 'reset-password',
          name: 'reset-password',
          component: () => import('@/views/ResetPasswordView.vue')
        },
        {
          path: 'verify-email',
          name: 'verify-email',
          component: () => import('@/views/VerifyEmailView.vue')
        },
        {
          path: 'user',
          name: 'user',
          component: () => import('@/views/UserView.vue'),
          meta: { requiresAuth: true }
        }
      ]
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') }
  ]
})

// 路由守卫：不能用 document.cookie 判断登录态 —— token 是 HttpOnly，JS 读不到。
// 正确做法是让 store 调 /auth/me 拿用户态（后端返回 401 时视为未登录）。
// 路由守卫：需要登录的页面先请求 /auth/me，未登录时带 redirect 回登录页
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true

  const userStore = useUserStore()
  const user = await userStore.fetchMe()
  if (!user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

// 每次切换路由先恢复站点默认 SEO，文章页加载后再覆盖
router.afterEach(() => {
  resetSeo()
})

export default router
