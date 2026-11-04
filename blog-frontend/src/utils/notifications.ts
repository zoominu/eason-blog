import type { Router } from 'vue-router'
import type { NotificationItem } from '@/api/types'
import { adminBase } from '@/utils/admin'

/**
 * 打开一条通知：
 * - `/admin/...` 属于后台，跳完整后台地址（同域 /admin/ 或开发 5174）；
 * - 其余按站内路径跳转（文章通知带 ?comment= 会自动定位并高亮）。
 */
export function openNotification(item: NotificationItem, router: Router) {
  const link = item.link
  if (!link) return
  if (link.startsWith('/admin')) {
    window.open(`${adminBase()}${link.replace('/admin', '')}`, '_blank', 'noopener')
    return
  }
  router.push(link)
}
