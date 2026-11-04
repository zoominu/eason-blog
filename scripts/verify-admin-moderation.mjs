/**
 * 后台审核页交互验收：行内无通过/拒绝按钮，顶部按钮随选中数量切换文案，并截图。
 * 用法：node verify-admin-moderation.mjs <baseUrl> <outPng>
 */
import { writeFileSync } from 'node:fs'

const [base, out] = process.argv.slice(2)
const host = new URL(base).hostname

const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@eason.blog', password: 'Admin123456' })
})
const token = (login.headers.get('set-cookie') ?? '').split(';')[0].replace('blog_token=', '')

const list = await (await fetch('http://127.0.0.1:9333/json/list')).json()
const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pending = new Map()
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data)
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id) }
})
await new Promise((resolve, reject) => {
  ws.addEventListener('open', resolve)
  ws.addEventListener('error', reject)
})
const send = (method, params = {}) => new Promise((resolve) => {
  const id = ++seq
  pending.set(id, resolve)
  ws.send(JSON.stringify({ id, method, params }))
})
const evaluate = async (expression, userGesture = false) => {
  const res = await send('Runtime.evaluate', { expression, awaitPromise: true, returnByValue: true, userGesture })
  return res.result?.result?.value
}

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Network.setCookie', { name: 'blog_token', value: token, domain: host, path: '/', httpOnly: true })
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await send('Page.navigate', { url: base + '/admin/comments' })
await new Promise((r) => setTimeout(r, 5500))

const before = await evaluate(`(() => {
  const rows = [...document.querySelectorAll('.el-table__body tr')]
  const rowButtons = rows.flatMap((row) => [...row.querySelectorAll('button')].map((b) => b.textContent.trim()))
  const top = [...document.querySelectorAll('.filters button')].map((b) => b.textContent.trim())
  return { rowButtons, top, rows: rows.length }
})()`)
console.log('before:', JSON.stringify(before))

const batch = await evaluate(`(async () => {
  const header = document.querySelector('.el-table__header .el-checkbox')
  if (!header) return { error: 'no-checkbox' }
  header.click()
  await new Promise((r) => setTimeout(r, 500))
  const labels = [...document.querySelectorAll('.filters button')].map((b) => b.textContent.trim())
  return { labels }
})()`, true)
console.log('after select-all:', JSON.stringify(batch))

const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
console.log('saved', out)

const noRowActions = before.rowButtons.every((t) => !['通过', '拒绝', '删除'].includes(t))
const hasTopActions = before.top.includes('通过') && before.top.includes('拒绝') && before.top.includes('删除')
const batchLabels = (batch.labels ?? []).some((t) => t.includes('批量通过'))
process.exit(noRowActions && hasTopActions && batchLabels ? 0 : 1)
