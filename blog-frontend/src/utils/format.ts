/** 展示层格式化工具，统一日期/数字样式，避免各页面各写一套。 */

export function formatDate(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function formatDateTime(value?: string | null): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${formatDate(value)} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function formatCount(value?: number | null): string {
  const count = value ?? 0
  if (count >= 10000) return `${(count / 10000).toFixed(1)}w`
  if (count >= 1000) return `${(count / 1000).toFixed(1)}k`
  return String(count)
}

function pad(value: number): string {
  return value < 10 ? `0${value}` : String(value)
}
