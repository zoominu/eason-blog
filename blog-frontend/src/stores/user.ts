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
    async login(email: string, password: string) {
      this.user = await authApi.login(email, password)
      this.initialized = true
      return this.user
    },
    async register(payload: { email: string; password: string; nickname?: string; code?: string }) {
      return authApi.register(payload)
    },
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.user = null
      }
    }
  }
})
