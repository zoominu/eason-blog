/**
 * 在真实部署的 http 站点上点击「复制链接」，验证非安全上下文下的回退逻辑。
 * 用法：node test-copy-resource.mjs <baseUrl> <adminEmail> <adminPassword> <outPng>
 */
import { writeFileSync } from 'node:fs'

const [base, email, password, out] = process.argv.slice(2)
const debugPort = 9333
const host = new URL(base).hostname

const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password })
})
const cookie = (login.headers.get('set-cookie') ?? '').split(';')[0].replace('blog_token=', '')

const list = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json()
const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
if (!page) throw new Error('no page target')

const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pending = new Map()
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data)
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg)
    pending.delete(msg.id)
  }
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

await send('Page.enable')
await send('Runtime.enable')
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Network.enable')
await send('Network.setCookie', {
  name: 'blog_token',
  value: cookie,
  domain: host,
  path: '/',
  httpOnly: true
})
await send('Page.navigate', { url: base + '/admin/resources' })
await new Promise((r) => setTimeout(r, 6000))
await send('Page.bringToFront')
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false
})

const result = await send('Runtime.evaluate', {
  expression: `(async () => {
    const secure = window.isSecureContext
    const hasClipboard = Boolean(navigator.clipboard)
    const btn = [...document.querySelectorAll('button')].find((b) => b.textContent.includes('复制链接'))
    if (!btn) return { ok: false, reason: 'no button', secure, hasClipboard }
    btn.click()
    await new Promise((r) => setTimeout(r, 700))
    const msg = document.querySelector('.el-message__content')?.textContent?.trim() ?? ''
    return { ok: msg.includes('已复制'), msg, secure, hasClipboard }
  })()`,
  awaitPromise: true,
  returnByValue: true,
  userGesture: true
})

const value = result.result?.result?.value
console.log(JSON.stringify(value))
const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
console.log('saved', out)
process.exit(value && value.ok ? 0 : 1)
