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
    /** 应用启动时调用一次，恢复登录态；401 属于正常情况，不视为错误。 */
    async fetchMe() {
      if (this.initialized) return
      try {
        this.user = await authApi.me()
      } catch {
        this.user = null
      } finally {
        this.initialized = true
      }
    },
    async login(email: string, password: string) {
      this.user = await authApi.login(email, password)
      this.initialized = true
      return this.user
    },
    async register(payload: { email: string; password: string; nickname?: string }) {
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
