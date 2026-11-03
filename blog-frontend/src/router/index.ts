import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layouts/MainLayout.vue'

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
        { path: 'search', name: 'search', component: () => import('@/views/BlogView.vue') },
        { path: 'guestbook', name: 'guestbook', component: () => import('@/views/GuestbookView.vue') },
        { path: 'about', name: 'about', component: () => import('@/views/AboutView.vue') },
        { path: 'login', name: 'login', component: () => import('@/views/LoginView.vue') },
        { path: 'register', name: 'register', component: () => import('@/views/RegisterView.vue') },
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

// 前端路由守卫只负责体验，真正的权限校验在后端
router.beforeEach((to) => {
  const tokenExisted = document.cookie.includes('blog_token=')
  if (to.meta.requiresAuth && !tokenExisted) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
