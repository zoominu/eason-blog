/**
 * 后台/首页 v2 验收：无回收站、用户头像、资源分类与引用、首页缩略图、文章页无封面图。
 * 用法：node verify-admin-v2.mjs <baseUrl> <imagePath>
 */
import { readFileSync } from 'node:fs'

const [base, imagePath] = process.argv.slice(2)
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

const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@eason.blog', password: 'Admin123456' })
})
const adminCookie = (login.headers.get('set-cookie') ?? '').split(';')[0]
const adminAuth = { Cookie: adminCookie }
record('管理员登录', Boolean(adminCookie))

// 上传一张图片作为文章正文图
const form = new FormData()
form.append('file', new Blob([readFileSync(imagePath)], { type: 'image/png' }), 'home-thumb.png')
const upload = await call('/api/v1/admin/resources/upload', { method: 'POST', headers: adminAuth, body: form })
const imageUrl = upload.body?.data?.url
record('资源上传成功', Boolean(imageUrl), imageUrl)

// 创建一篇正文带图的文章
const slug = 'ui-v2-' + Date.now().toString().slice(-5)
const created = await call('/api/v1/admin/articles', {
  method: 'POST',
  headers: { ...adminAuth, 'Content-Type': 'application/json' },
  body: JSON.stringify({
    title: `UI 验收文章 ${slug}`,
    slug,
    content: `正文里的图片：\n\n![缩略图](${imageUrl})\n`,
    summary: '用于验证首页缩略图与文章页不重复展示封面',
    status: 1
  })
})
const articleId = created.body?.data?.id
record('创建带图文章', Boolean(articleId), `id=${articleId}`)

// ---------- 浏览器 ----------
const page = (await (await fetch('http://127.0.0.1:9333/json/list')).json())
  .find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pending = new Map()
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data)
  if (msg.id && pending.has(msg.id)) { pending.get(msg.id)(msg); pending.delete(msg.id) }
  if (msg.method === 'Page.javascriptDialogOpening') {
    ws.send(JSON.stringify({ id: ++seq, method: 'Page.handleJavaScriptDialog', params: { accept: true } }))
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
await send('Network.setCookie', {
  name: 'blog_token', value: adminCookie.replace('blog_token=', ''),
  domain: new URL(base).hostname, path: '/', httpOnly: true
})
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

// 1) 文章管理没有回收站
await goto('/admin/articles')
const articleUi = await evaluate(`(async () => {
  const select = document.querySelector('.filters .el-select')
  select.click()
  await new Promise((r) => setTimeout(r, 500))
  const options = [...document.querySelectorAll('.el-select-dropdown__item')].map((o) => o.textContent.trim())
  document.body.click()
  await new Promise((r) => setTimeout(r, 200))
  const buttons = [...document.querySelectorAll('.el-table__row button')].map((b) => b.textContent.trim())
  const header = [...document.querySelectorAll('.filters .el-select')].map((s) => s.textContent.trim())
  return { options, buttons: [...new Set(buttons)], header }
})()`, true)
record('状态筛选没有「回收站」', !articleUi?.options?.includes('回收站'), JSON.stringify(articleUi?.options))
record('行内没有「回收/恢复」', !articleUi?.buttons?.some((b) => ['回收', '恢复'].includes(b)), JSON.stringify(articleUi?.buttons))
record('行内有「删除」', articleUi?.buttons?.includes('删除'))

// 2) 用户列表展示头像
await goto('/admin/users')
const userUi = await evaluate(`(() => {
  const imgs = [...document.querySelectorAll('.el-table__row .el-avatar img')]
  return { count: imgs.length, src: imgs[0]?.getAttribute('src') ?? '' }
})()`)
record('用户列表展示头像', userUi?.count > 0 && userUi?.src?.includes('/uploads/'), JSON.stringify(userUi))

// 3) 资源分类 + 引用
await goto('/admin/resources', 5500)
const resUi = await evaluate(`(async () => {
  const select = document.querySelector('.filters .el-select')
  select.click()
  await new Promise((r) => setTimeout(r, 500))
  const options = [...document.querySelectorAll('.el-select-dropdown__item')].map((o) => o.textContent.trim())
  document.body.click()
  await new Promise((r) => setTimeout(r, 200))
  const tags = [...new Set([...document.querySelectorAll('.card-item .el-tag')].map((t) => t.textContent.trim()))]
  const refButtons = [...document.querySelectorAll('.card-item button')].filter((b) => b.textContent.includes('引用'))
  return { options, tags, refButtons: refButtons.length }
})()`, true)
record('资源类型筛选有 6 类', ['头像', '文章图片', '音乐', '视频', '文档', '其他'].every((t) => resUi?.options?.includes(t)), JSON.stringify(resUi?.options))
record('资源卡片显示类型标签', (resUi?.tags ?? []).length > 0, JSON.stringify(resUi?.tags))

const refDialog = await evaluate(`(async () => {
  const buttons = [...document.querySelectorAll('.card-item button')].filter((b) => b.textContent.includes('引用'))
  const target = buttons.find((b) => !b.textContent.includes('(0)')) ?? buttons[0]
  if (!target) return { error: 'no-button' }
  target.click()
  await new Promise((r) => setTimeout(r, 1200))
  const links = [...document.querySelectorAll('.refs__link')].map((a) => a.textContent.trim())
  const title = document.querySelector('.el-dialog__title')?.textContent?.trim() ?? ''
  return { title, links }
})()`, true)
record('资源可查看引用文章', (refDialog?.links ?? []).some((t) => t.includes(slug)), JSON.stringify(refDialog))

// 4) 首页缩略图
await goto('/')
const home = await evaluate(`(() => {
  const cards = [...document.querySelectorAll('.article-card')]
  const card = cards.find((c) => c.textContent.includes(${JSON.stringify(slug)}))
  const img = card?.querySelector('.article-card__cover')
  return { found: Boolean(card), hasImg: Boolean(img), src: img?.getAttribute('src') ?? '',
           loaded: img ? img.naturalWidth > 0 : false }
})()`)
record('首页文章卡片展示缩略图', home?.hasImg === true && home?.loaded === true, JSON.stringify(home))

// 5) 文章页不再展示封面图
await goto(`/article/${slug}`)
const articlePage = await evaluate(`(() => {
  const cover = document.querySelector('.cover')
  const contentImg = document.querySelector('.prose-blog img')
  return { hasCover: Boolean(cover), contentImg: contentImg?.getAttribute('src') ?? '' }
})()`)
record('文章页顶部不再展示缩略图', articlePage?.hasCover === false, JSON.stringify(articlePage))
record('正文图片仍然展示', Boolean(articlePage?.contentImg))

// 清理：删除验收文章
await call(`/api/v1/admin/articles/${articleId}`, { method: 'DELETE', headers: adminAuth })

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
