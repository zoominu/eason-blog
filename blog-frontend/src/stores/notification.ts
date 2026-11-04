import { defineStore } from 'pinia'
import { interactionApi } from '@/api'
import type { NotificationItem } from '@/api/types'
import { useUserStore } from '@/stores/user'

/**
 * 通知状态：未读数轮询 + 新通知推送。
 *
 * 每 30 秒拉一次未读数（很轻），发现变大再取最新一条用于弹出提示；
 * 不引入 WebSocket：个人博客单实例，轮询足够且失败无副作用。
 */
export const useNotificationStore = defineStore('notification', {
  state: () => ({
    unread: 0,
    latest: null as NotificationItem | null,
    /** 最新通知的序号，用于 watch 触发提示 */
    latestSeq: 0,
    timer: 0 as number | ReturnType<typeof setInterval> | 0
  }),

  actions: {
    async fetchUnread() {
      const userStore = useUserStore()
      if (!userStore.isLoggedIn) {
        this.unread = 0
        return 0
      }
      try {
        const result = await interactionApi.unreadCount()
        const previous = this.unread
        this.unread = result.count
        // 首次加载不弹提示；之后只在新通知到来时提示
        if (previous > 0 && result.count > previous) {
          const items = await interactionApi.notifications(1)
          if (items.length) {
            this.latest = items[0]
            this.latestSeq += 1
          }
        }
        return result.count
      } catch {
        return this.unread
      }
    },

    /** 打开通知页时把列表同步到最新（未读数清零前也能拿到内容） */
    async refreshLatest() {
      try {
        const items = await interactionApi.notifications(1)
        if (items.length) {
          this.latest = items[0]
          this.latestSeq += 1
        }
      } catch {
        // 忽略：轮询会继续尝试
      }
    },

    startPolling(intervalMs = 30000) {
      if (this.timer) return
      this.fetchUnread()
      this.timer = setInterval(() => this.fetchUnread(), intervalMs)
    },

    stopPolling() {
      if (this.timer) {
        clearInterval(this.timer)
        this.timer = 0
      }
    },

    async markAllRead() {
      const result = await interactionApi.markAllRead()
      this.unread = 0
      return result.updated
    },

    async deleteRead() {
      const result = await interactionApi.deleteRead()
      await this.fetchUnread()
      return result.deleted
    }
  }
})
