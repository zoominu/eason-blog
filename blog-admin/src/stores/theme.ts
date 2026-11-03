import { defineStore } from 'pinia'

const STORAGE_KEY = 'eason-admin-theme'

/**
 * 后台主题：浅色 / 深色，持久化到 localStorage。
 * Element Plus 约定用 html.dark 切换暗色变量。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    dark: false
  }),
  actions: {
    init() {
      const saved = localStorage.getItem(STORAGE_KEY)
      this.dark = saved === 'dark'
      this.apply()
    },
    toggle() {
      this.dark = !this.dark
      localStorage.setItem(STORAGE_KEY, this.dark ? 'dark' : 'light')
      this.apply()
    },
    apply() {
      document.documentElement.classList.toggle('dark', this.dark)
    }
  }
})
