/**
 * 登录用户状态。
 *
 * <p>用户信息只保存在内存；刷新页面后通过 `fetchMe()` 重新拉取。
 * token 是 HttpOnly Cookie，前端无法也不应读取。</p>
 */
import { defineStore } from 'pinia'
import { authApi } from '@/api'
import type { User } from '@/api/types'

export const useUserStore = defineStore('user', {
  state: () => ({
    user: null as User | null,
    initialized: false
  }),
  getters: {
    isLoggedIn: (state) => state.user !== null,
    isAdmin: (state) => state.user?.roles.includes('SUPER_ADMIN') ?? false
  },
  actions: {
    /** 恢复登录态；返回当前用户（未登录为 null），供路由守卫判断。 */
    async fetchMe(): Promise<User | null> {
      if (this.initialized) return this.user
      try {
        this.user = await authApi.me()
      } catch {
        this.user = null
      } finally {
        this.initialized = true
      }
      return this.user
    },
    /** 登录：account 为站内账号名；成功后写入 user，失败抛错由页面展示。 */
    async login(account: string, password: string) {
      this.user = await authApi.login(account, password)
      this.initialized = true
      return this.user
    },
    /** 注册：成功后不自动登录，由页面决定是否调用 login。 */
    async register(payload: { email: string; password: string; username?: string; nickname?: string; code?: string }) {
      return authApi.register(payload)
    },
    /** 退出：无论后端是否成功都清空本地用户，避免界面残留登录态。 */
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.user = null
      }
    }
  }
})
