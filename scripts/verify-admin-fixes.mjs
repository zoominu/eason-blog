/**
 * 后台修复验收：专栏加文章、留言回复、设置手风琴。
 * 用法：node verify-admin-fixes.mjs <baseUrl> <outPng>
 */
import { writeFileSync } from 'node:fs'

const [base, out] = process.argv.slice(2)
const host = new URL(base).hostname
const results = []

function record(name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`)
}

async function call(path, options = {}) {
  const res = await fetch(base + path, options)
  const text = await res.text()
  let body
  try { body = JSON.parse(text) } catch { body = text }
  return { status: res.status, body }
}

async function login(email, password) {
  const res = await fetch(base + '/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  })
  return (res.headers.get('set-cookie') ?? '').split(';')[0]
}

const adminCookie = await login('admin@eason.blog', 'Admin123456')
const readerCookie = await login('reader2@eason.blog', 'Test123456')
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }
const readerAuth = { Cookie: readerCookie, 'Content-Type': 'application/json' }

// 造一条待审核留言（命中违禁词），供「回复」按钮测试
const suffix = Date.now().toString().slice(-5)
await call('/api/v1/admin/settings', {
  method: 'PATCH', headers: adminAuth, body: JSON.stringify({ 'audit.banned-words': '待审词' })
})
await call('/api/v1/guestbook', {
  method: 'POST', headers: readerAuth, body: JSON.stringify({ content: `手风琴测试留言-${suffix}-待审词` })
})
await call('/api/v1/admin/settings', {
  method: 'PATCH', headers: adminAuth, body: JSON.stringify({ 'audit.banned-words': '' })
})
record('准备待审核留言', true)

const page = (await (await fetch('http://127.0.0.1:9333/json/list')).json())
  .find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
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
const goto = async (path, waitMs = 5000) => {
  await send('Page.navigate', { url: base + path })
  await new Promise((r) => setTimeout(r, waitMs))
}

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Network.setCookie', { name: 'blog_token', value: adminCookie.replace('blog_token=', ''), domain: host, path: '/', httpOnly: true })
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

// 1) 专栏管理 → 管理文章 → 保存
await goto('/admin/columns')
const columns = await evaluate(`(async () => {
  const btn = [...document.querySelectorAll('.el-table__row button')].find((b) => b.textContent.trim() === '文章')
  if (!btn) return { error: 'no-article-button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 900))
  const select = document.querySelector('.el-dialog .el-select')
  if (!select) return { error: 'no-select' }
  select.click()
  await new Promise((r) => setTimeout(r, 500))
  const option = document.querySelector('.el-select-dropdown__item')
  if (!option) return { error: 'no-option' }
  option.click()
  await new Promise((r) => setTimeout(r, 300))
  const save = [...document.querySelectorAll('.el-dialog button')].find((b) => b.textContent.trim() === '保存')
  if (!save) return { error: 'no-save' }
  save.click()
  await new Promise((r) => setTimeout(r, 1500))
  return { message: document.querySelector('.el-message__content')?.textContent?.trim() ?? '' }
})()`, true)
record('专栏添加文章成功', /专栏文章已更新/.test(columns?.message ?? ''), JSON.stringify(columns))

// 2) 留言管理 → 回复 → 保存
await goto('/admin/guestbook')
const reply = await evaluate(`(async () => {
  const btn = [...document.querySelectorAll('.el-table__row button')].find((b) => b.textContent.trim() === '回复')
  if (!btn) return { error: 'no-reply-button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 800))
  const textarea = document.querySelector('.el-dialog textarea')
  if (!textarea) return { error: 'no-textarea' }
  textarea.value = '手风琴验证回复'
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
  await new Promise((r) => setTimeout(r, 200))
  const save = [...document.querySelectorAll('.el-dialog button')].find((b) => b.textContent.trim() === '保存回复')
  if (!save) return { error: 'no-save' }
  save.click()
  await new Promise((r) => setTimeout(r, 1500))
  return { message: document.querySelector('.el-message__content')?.textContent?.trim() ?? '' }
})()`, true)
record('留言回复成功', /回复已保存/.test(reply?.message ?? ''), JSON.stringify(reply))

// 3) 设置页手风琴
await goto('/admin/settings', 5500)
const settings = await evaluate(`(() => {
  const collapse = document.querySelector('.el-collapse')
  const items = [...document.querySelectorAll('.el-collapse-item')]
  const active = document.querySelectorAll('.el-collapse-item.is-active').length
  const titles = items.map((i) => i.querySelector('.el-collapse-item__header')?.textContent?.trim() ?? '')
  return { hasCollapse: Boolean(collapse), count: items.length, active, titles }
})()`)
record('设置页使用手风琴分组', settings?.hasCollapse === true && settings.count >= 5, JSON.stringify(settings))
record('默认只展开一组', settings?.active === 1, `active=${settings?.active}`)

const firstTitle = settings?.titles?.[0] ?? ''
const toggled = await evaluate(`(async () => {
  const headers = [...document.querySelectorAll('.el-collapse-item__header')]
  const second = headers[1]
  if (!second) return { error: 'no-second' }
  second.click()
  await new Promise((r) => setTimeout(r, 600))
  return { active: document.querySelectorAll('.el-collapse-item.is-active').length,
           title: second.textContent.trim() }
})()`, true)
record('点击其它分组会切换展开', toggled?.active === 1 && toggled?.title?.length > 0, JSON.stringify(toggled))

const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
console.log('saved', out)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
