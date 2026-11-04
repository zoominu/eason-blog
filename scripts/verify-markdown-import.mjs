/**
 * Markdown 导入验收：上传 .md + 随文图片，正文/图片自动上传并改写，发布后前台可直接展示。
 * 用法：node verify-markdown-import.mjs <baseUrl> <fixtureDir>
 */
import { resolve } from 'node:path'

const [base, fixtureDir] = process.argv.slice(2)
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
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }
record('管理员登录', Boolean(adminCookie))

// 清掉上一次验证留下的文章，避免 slug 冲突
const old = await call('/api/v1/admin/articles?keyword=' + encodeURIComponent('MD 导入验证') + '&size=20',
  { headers: { Cookie: adminCookie } })
for (const item of old.body?.data?.records ?? []) {
  await call(`/api/v1/admin/articles/${item.id}`, { method: 'DELETE', headers: { Cookie: adminCookie } })
}

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

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('DOM.enable')
await send('Network.setCookie', {
  name: 'blog_token', value: adminCookie.replace('blog_token=', ''),
  domain: new URL(base).hostname, path: '/', httpOnly: true
})
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })
await send('Page.navigate', { url: base + '/admin/articles/create' })
await new Promise((r) => setTimeout(r, 5000))

const doc = await send('DOM.getDocument')
const input = await send('DOM.querySelector', { nodeId: doc.result.root.nodeId, selector: 'input.md-input' })
record('新建文章页有「导入 MD」入口', Boolean(input.result?.nodeId))
if (input.result?.nodeId) {
  await send('DOM.setFileInputFiles', {
    nodeId: input.result.nodeId,
    files: [resolve(fixtureDir, 'post.md'), resolve(fixtureDir, 'shot.png')]
  })
  await new Promise((r) => setTimeout(r, 4000))
}

const imported = await evaluate(`(() => {
  const title = document.querySelector('.el-form-item input')?.value ?? ''
  const textarea = document.querySelector('.editor textarea')
  return {
    title,
    content: textarea?.value ?? '',
    message: document.querySelector('.el-message__content')?.textContent?.trim() ?? '',
    images: (textarea?.value ?? '').match(/!\\[[^\\]]*]\\([^)]+\\)/g) ?? []
  }
})()`)
record('标题被解析', imported?.title === 'MD 导入验证', JSON.stringify(imported.title))
record('正文包含上传后的图片地址',
  imported.images.length === 2 && imported.images.every((i) => i.includes('/uploads/')),
  JSON.stringify(imported.images))
record('H1 已从正文移除', !(imported.content ?? '').includes('# 这行标题会被忽略'))

// 导入的图片应进入资源库
const resources = await call('/api/v1/admin/resources?keyword=shot.png&size=10',
  { headers: { Cookie: adminCookie } })
record('图片已登记到资源库',
  (resources.body?.data?.records ?? []).some((r) => r.originalName === 'shot.png'),
  JSON.stringify((resources.body?.data?.records ?? []).map((r) => r.originalName)))

// 保存并发布
const saved = await evaluate(`(async () => {
  const btn = [...document.querySelectorAll('.head-actions button')].find((b) => b.textContent.includes('保存并发布'))
  if (!btn) return { error: 'no-save' }
  btn.click()
  await new Promise((r) => setTimeout(r, 3000))
  return { message: document.querySelector('.el-message__content')?.textContent?.trim() ?? '' }
})()`, true)
record('保存并发布成功', /已创建并发布|已保存并发布/.test(saved?.message ?? ''), JSON.stringify(saved))

// 前台打开文章，确认图片能展示
const found = await call('/api/v1/admin/articles?keyword=' + encodeURIComponent('MD 导入验证') + '&size=10',
  { headers: { Cookie: adminCookie } })
const article = (found.body?.data?.records ?? [])[0]
record('文章已创建', Boolean(article?.slug), article?.slug)

if (article?.slug) {
  await send('Page.navigate', { url: `${base}/article/${article.slug}` })
  await new Promise((r) => setTimeout(r, 5000))
  const rendered = await evaluate(`(() => {
    const imgs = [...document.querySelectorAll('.prose-blog img')]
    return {
      count: imgs.length,
      loaded: imgs.filter((i) => i.naturalWidth > 0).length,
      srcs: imgs.map((i) => i.getAttribute('src')),
      text: document.body.innerText.includes('这是一段通过 Markdown 导入的正文')
    }
  })()`)
  record('前台正文正常渲染', rendered?.text === true)
  record('图片随文章展示且加载成功',
    rendered?.count === 2 && rendered?.loaded === 2,
    JSON.stringify(rendered))
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
