/**
 * 个人中心 v2 验收：头像上传、收藏/点赞/评论/通知、批量管理、评论 3 分钟内删除。
 * 用法：node verify-profile.mjs [baseUrl]
 */
import { resolve } from 'node:path'

const base = process.argv[2] ?? 'http://192.168.245.131'
const avatarFile = resolve('.tools/shots/vm-settings-accordion.png')
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

const readerCookie = await login('reader1@eason.blog', 'Test123456')
const adminCookie = await login('admin@eason.blog', 'Admin123456')
const readerAuth = { Cookie: readerCookie, 'Content-Type': 'application/json' }
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }

const articles = (await call('/api/v1/articles?size=20')).body.data.records
const article = articles.find((a) => a.slug === 'one-command-dev-environment')
record('取到测试文章', Boolean(article))

const suffix = Date.now().toString().slice(-5)
const commentText = `个人中心评论-${suffix}`

// 准备数据：收藏 + 点赞 + 评论 + 一条回复通知
await call(`/api/v1/articles/${article.id}/favorite`, { method: 'POST', headers: readerAuth })
await call(`/api/v1/articles/${article.id}/like`, { method: 'POST', headers: readerAuth })
const created = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: readerAuth, body: JSON.stringify({ content: commentText })
})
const commentId = created.body?.data?.id
await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: adminAuth,
  body: JSON.stringify({ content: `个人中心回复-${suffix}`, parentId: commentId })
})
// 另建一条没有回复的评论，用于验证 3 分钟内可删除
const plainText = `可删除评论-${suffix}`
const plainCreated = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: readerAuth, body: JSON.stringify({ content: plainText })
})
const plainId = plainCreated.body?.data?.id
record('准备收藏/点赞/评论/回复', Boolean(commentId && plainId), `commentId=${commentId}, plainId=${plainId}`)

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
const host = new URL(base).hostname
const goto = async (url, waitMs = 5500) => {
  await send('Page.navigate', { url })
  await new Promise((r) => setTimeout(r, waitMs))
}

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('DOM.enable')
await send('Network.setCookie', { name: 'blog_token', value: readerCookie.replace('blog_token=', ''), domain: host, path: '/', httpOnly: true })
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

await goto(`${base}/user`)
const tabs = await evaluate(`[...document.querySelectorAll('.tabs button')].map((b) => b.textContent.trim())`)
record('个人中心有 5 个标签', ['资料', '收藏', '点赞', '评论', '通知'].every((t) => tabs.some((x) => x.includes(t))), JSON.stringify(tabs))

// 1) 头像上传
const doc = await send('DOM.getDocument')
const inputNode = await send('DOM.querySelector', { nodeId: doc.result.root.nodeId, selector: 'input[type=file]' })
if (inputNode.result?.nodeId) {
  await send('DOM.setFileInputFiles', { nodeId: inputNode.result.nodeId, files: [avatarFile] })
  await new Promise((r) => setTimeout(r, 3000))
}
const avatar = await evaluate(`(() => {
  const img = document.querySelector('.avatar')
  return { src: img?.getAttribute('src') ?? '', message: document.body.innerText.includes('头像已更新') }
})()`)
record('头像上传成功并展示', avatar?.src?.includes('/uploads/avatar/') === true, JSON.stringify(avatar))

// 2) 收藏批量取消
await evaluate(`[...document.querySelectorAll('.tabs button')].find((b) => b.textContent.includes('收藏')).click()`, true)
await new Promise((r) => setTimeout(r, 1200))
const fav = await evaluate(`(async () => {
  const row = [...document.querySelectorAll('.article-row')].find((r) => r.textContent.includes(${JSON.stringify(article.title)}))
  if (!row) return { error: 'no-row' }
  row.querySelector('input[type=checkbox]').click()
  await new Promise((r) => setTimeout(r, 200))
  const btn = [...document.querySelectorAll('.list-head button')].find((b) => b.textContent.includes('取消收藏'))
  if (!btn) return { error: 'no-button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 1500))
  return { message: document.body.innerText.includes('已取消收藏'), selected: document.querySelectorAll('.article-row input:checked').length }
})()`, true)
record('收藏可批量取消', fav?.message === true, JSON.stringify(fav))

// 3) 点赞批量取消
await evaluate(`[...document.querySelectorAll('.tabs button')].find((b) => b.textContent.includes('点赞')).click()`, true)
await new Promise((r) => setTimeout(r, 1200))
const like = await evaluate(`(async () => {
  const row = [...document.querySelectorAll('.article-row')].find((r) => r.textContent.includes(${JSON.stringify(article.title)}))
  if (!row) return { error: 'no-row' }
  row.querySelector('input[type=checkbox]').click()
  await new Promise((r) => setTimeout(r, 200))
  const btn = [...document.querySelectorAll('.list-head button')].find((b) => b.textContent.includes('取消点赞'))
  if (!btn) return { error: 'no-button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 1500))
  return { message: document.body.innerText.includes('已取消点赞') }
})()`, true)
record('点赞可批量取消', like?.message === true, JSON.stringify(like))

// 4) 我的评论：删除一条（3 分钟内）
await evaluate(`[...document.querySelectorAll('.tabs button')].find((b) => b.textContent.includes('评论')).click()`, true)
await new Promise((r) => setTimeout(r, 1300))
const myComment = await evaluate(`(async () => {
  const row = [...document.querySelectorAll('.comment-row')].find((r) => r.textContent.includes(${JSON.stringify(plainText)}))
  if (!row) return { error: 'no-row' }
  const del = [...row.querySelectorAll('button')].find((b) => b.textContent.trim() === '删除')
  if (!del) return { error: 'no-delete-button' }
  del.click()
  await new Promise((r) => setTimeout(r, 1600))
  return { message: document.body.innerText.includes('评论已删除'),
           remaining: document.body.innerText.includes(${JSON.stringify(plainText)}) }
})()`, true)
record('3 分钟内的评论可删除', myComment?.message === true && myComment.remaining === false, JSON.stringify(myComment))

// 5) 通知分类
await evaluate(`[...document.querySelectorAll('.tabs button')].find((b) => b.textContent.includes('通知')).click()`, true)
await new Promise((r) => setTimeout(r, 1500))
const chips = await evaluate(`[...document.querySelectorAll('.chip')].map((c) => c.textContent.trim())`)
record('通知有分类筛选', ['全部', '评论', '回复', '系统'].every((t) => chips.includes(t)), JSON.stringify(chips))

const replyFilter = await evaluate(`(async () => {
  const chip = [...document.querySelectorAll('.chip')].find((c) => c.textContent.trim() === '回复')
  chip.click()
  await new Promise((r) => setTimeout(r, 1200))
  const titles = [...document.querySelectorAll('.notice__title')].map((t) => t.textContent.trim())
  return { active: document.querySelector('.chip--active')?.textContent?.trim(), titles }
})()`, true)
record('按「回复」筛选通知', replyFilter?.active === '回复'
  && replyFilter.titles.length > 0
  && replyFilter.titles.every((t) => t.includes('回复')), JSON.stringify(replyFilter))

// 6) 文章页评论 3 分钟内可删除
const artText = `文章页删除-${suffix}`
const artComment = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: readerAuth, body: JSON.stringify({ content: artText })
})
const artId = artComment.body?.data?.id
await goto(`${base}/article/${article.slug}?comment=${artId}`, 5500)
const articleDelete = await evaluate(`(async () => {
  const node = document.getElementById('comment-${artId}')
  if (!node) return { error: 'no-node' }
  const del = node.querySelector(':scope > .comment__actions .comment__delete')
  if (!del) return { error: 'no-delete-button' }
  del.click()
  await new Promise((r) => setTimeout(r, 1800))
  return { removed: !document.getElementById('comment-${artId}') }
})()`, true)
record('文章页自己的评论可删除', articleDelete?.removed === true, JSON.stringify(articleDelete))

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
