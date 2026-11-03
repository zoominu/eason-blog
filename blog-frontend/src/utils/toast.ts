import { reactive } from 'vue'

export interface ToastItem {
  id: number
  message: string
  type: 'success' | 'error' | 'info'
}

/** 全局轻提示队列；由 ToastHost 渲染，任何页面都可以调用。 */
export const toasts = reactive<ToastItem[]>([])

let seq = 0

export function showToast(message: string, type: ToastItem['type'] = 'success', duration = 2400) {
  const id = ++seq
  toasts.push({ id, message, type })
  setTimeout(() => {
    const index = toasts.findIndex((item) => item.id === id)
    if (index >= 0) toasts.splice(index, 1)
  }, duration)
}
