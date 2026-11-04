/**
 * 后台地址解析。
 *
 * 生产部署在站点同域的 /admin/ 子路径，开发环境是单独的 5174 端口。
 * 之前硬编码 5174 导致线上通知跳转到 http://ip:5174 直接被拒绝连接。
 */
export function adminBase(): string {
  const configured = import.meta.env.VITE_ADMIN_URL
  if (configured) return configured.replace(/\/$/, '')
  if (import.meta.env.DEV) {
    return `${window.location.protocol}//${window.location.hostname}:5174`
  }
  return `${window.location.origin}/admin`
}
