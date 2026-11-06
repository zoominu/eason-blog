/**
 * 后台登录态：当前管理员信息、登录/退出 action；页面刷新后通过 /auth/me 恢复。
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
    isAdmin: (state) => state.user?.roles.includes('SUPER_ADMIN') ?? false
  },
  actions: {
    async fetchMe() {
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
    async logout() {
      try {
        await authApi.logout()
      } finally {
        this.user = null
      }
    }
  }
})
