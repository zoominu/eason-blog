import { defineStore } from 'pinia'

export type ThemeMode = 'light' | 'dark' | 'system'

const STORAGE_KEY = 'eason-blog-theme'

/**
 * 主题：默认「跟随系统」，用户可以手动锁定浅色/深色。
 *
 * 初始值有三个来源，优先级从高到低：
 *  1. localStorage 中用户上次的手动选择；
 *  2. 系统当前的 prefers-color-scheme；
 *  3. 浅色。
 * index.html 里的内联脚本已经做过一次同样的判断（防闪烁），
 * 这里读取 data-theme-mode 保持两侧一致，而不是再猜一次。
 */
export const useThemeStore = defineStore('theme', {
  state: () => ({
    mode: 'system' as ThemeMode,
    resolved: 'light' as 'light' | 'dark'
  }),
  getters: {
    isDark: (state) => state.resolved === 'dark',
    modeLabel: (state) =>
      state.mode === 'system' ? '跟随系统' : state.mode === 'dark' ? '深色' : '浅色'
  },
  actions: {
    init() {
      const saved = localStorage.getItem(STORAGE_KEY)
      const initial = document.documentElement.getAttribute('data-theme-mode')
      if (saved === 'light' || saved === 'dark' || saved === 'system') {
        this.mode = saved
      } else if (initial === 'light' || initial === 'dark' || initial === 'system') {
        this.mode = initial
      }
      this.apply()

      // 系统主题变化时，只有「跟随系统」模式才响应，手动选择不会被覆盖
      window
        .matchMedia('(prefers-color-scheme: dark)')
        .addEventListener('change', () => {
          if (this.mode === 'system') {
            this.apply()
          }
        })
    },
    setMode(mode: ThemeMode) {
      this.mode = mode
      localStorage.setItem(STORAGE_KEY, mode)
      this.apply()
    },
    toggle() {
      // 三态循环：跟随系统 → 浅色 → 深色 → 跟随系统
      const next: ThemeMode =
        this.mode === 'system' ? 'light' : this.mode === 'light' ? 'dark' : 'system'
      this.setMode(next)
    },
    apply() {
      const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
      this.resolved = this.mode === 'system' ? (prefersDark ? 'dark' : 'light') : this.mode
      const root = document.documentElement
      root.setAttribute('data-theme', this.resolved)
      root.setAttribute('data-theme-mode', this.mode)
    }
  }
})
