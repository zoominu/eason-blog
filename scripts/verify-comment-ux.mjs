/**
 * 评论/通知交互验收（真实浏览器 + 真实接口）。
 * 覆盖：通知后台链接、回复按钮 hover 隐藏、展开按钮位置、折叠评论跳转自动展开并高亮、通知轮询弹窗。
 * 用法：node verify-comment-ux.mjs [baseUrl]
 */
const base = process.argv[2] ?? 'http://192.168.245.131'
const debugPort = 9333
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
  const cookie = (res.headers.get('set-cookie') ?? '').split(';')[0]
  return { status: res.status, cookie }
}

const admin = await login('admin@eason.blog', 'Admin123456')
const reader1 = await login('reader1@eason.blog', 'Test123456')
const reader2 = await login('reader2@eason.blog', 'Test123456')
record('三个账号登录', admin.status === 200 && reader1.status === 200 && reader2.status === 200)

const adminAuth = { Cookie: admin.cookie, 'Content-Type': 'application/json' }
const r1Auth = { Cookie: reader1.cookie, 'Content-Type': 'application/json' }
const r2Auth = { Cookie: reader2.cookie, 'Content-Type': 'application/json' }

const list = await call('/api/v1/articles?size=1')
const article = list.body.data.records[0]
record('取到测试文章', Boolean(article?.id), article?.slug)

const suffix = Date.now().toString().slice(-5)

// 1) 顶层评论（待审核）→ 通过
const root = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: r1Auth, body: JSON.stringify({ content: `根评论-${suffix}` })
})
const rootId = root.body?.data?.id
await call(`/api/v1/admin/comments/${rootId}/approve`, { method: 'POST', headers: adminAuth })
record('顶层评论已通过', Boolean(rootId), `id=${rootId}`)

// 2) reader2 回复 5 条（限流 5/分钟），全部审核通过
const replyIds = []
for (let i = 1; i <= 5; i++) {
  const r = await call(`/api/v1/articles/${article.id}/comments`, {
    method: 'POST', headers: r2Auth,
    body: JSON.stringify({ content: `回复-${i}-${suffix}`, parentId: rootId })
  })
  if (r.status === 200) {
    replyIds.push(r.body.data.id)
    await call(`/api/v1/admin/comments/${r.body.data.id}/approve`, { method: 'POST', headers: adminAuth })
  }
}
record('5 条回复已通过', replyIds.length === 5, `count=${replyIds.length}`)

// 3) 博主回复第 6 条（免审，直接发布）
const adminReply = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: adminAuth,
  body: JSON.stringify({ content: `博主回复-6-${suffix}`, parentId: rootId })
})
const adminReplyId = adminReply.body?.data?.id
record('博主评论免审核', adminReply.status === 200, `id=${adminReplyId}`)

const publicComments = await call(`/api/v1/articles/${article.slug}/comments?size=50`)
const rootNode = (publicComments.body?.data?.records ?? []).find((c) => c.id === rootId)
record('博主回复已直接可见', (rootNode?.replies ?? []).some((r) => r.id === adminReplyId))
record('回复总数 6 条', (rootNode?.replies ?? []).length === 6, `replies=${rootNode?.replies?.length}`)

// 4) 管理员通知：reader1 再发一条待审核评论，作为最新通知
const pending = await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: r1Auth, body: JSON.stringify({ content: `待审评论-${suffix}` })
})
record('生成待审核评论', pending.status === 200)

// ---------- 浏览器部分 ----------
const targets = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json()
const page = targets.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
if (!page) throw new Error('没有可用的浏览器页面')

const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pendingMap = new Map()
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data)
  if (msg.id && pendingMap.has(msg.id)) {
    pendingMap.get(msg.id)(msg)
    pendingMap.delete(msg.id)
  }
})
await new Promise((resolve, reject) => {
  ws.addEventListener('open', resolve)
  ws.addEventListener('error', reject)
})
const send = (method, params = {}) => new Promise((resolve) => {
  const id = ++seq
  pendingMap.set(id, resolve)
  ws.send(JSON.stringify({ id, method, params }))
})

const host = new URL(base).hostname
const evaluate = async (expression, userGesture = false) => {
  const res = await send('Runtime.evaluate', {
    expression, awaitPromise: true, returnByValue: true, userGesture
  })
  return res.result?.result?.value
}
const goto = async (url, cookieValue, waitMs = 5500) => {
  await send('Network.setCookie', {
    name: 'blog_token', value: cookieValue, domain: host, path: '/', httpOnly: true
  })
  await send('Page.navigate', { url })
  await new Promise((r) => setTimeout(r, waitMs))
}
const tokenOf = (cookie) => cookie.replace('blog_token=', '')

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false
})

// 4.1 管理员通知点击 → 后台地址必须是同域 /admin/，而不是 :5174
await goto(`${base}/user?tab=notifications`, tokenOf(admin.cookie))
const adminLink = await evaluate(`(async () => {
  window.__opened = []
  window.open = (u) => { window.__opened.push(String(u)); return null }
  const notices = [...document.querySelectorAll('button.notice')]
  const target = notices.find((n) => (n.textContent || '').includes('待审核'))
    || notices[0]
  if (!target) return { error: 'no-notification' }
  target.click()
  await new Promise((r) => setTimeout(r, 900))
  return { url: window.__opened[0] ?? '', text: target.textContent.slice(0, 40) }
})()`, true)
record('管理员通知跳转到 /admin/ 路径',
  String(adminLink?.url ?? '').includes('/admin/comments?highlight='),
  adminLink?.url ?? JSON.stringify(adminLink))

// 4.2 文章页：回复按钮默认隐藏、展开按钮在末尾、折叠只显示 3 条
await goto(`${base}/article/${article.slug}`, tokenOf(reader1.cookie))
const ui = await evaluate(`(() => {
  const replyBtn = document.querySelector('.comment__reply')
  const replies = [...document.querySelectorAll('.replies')].find((el) => el.children.length)
  const last = replies ? replies.children[replies.children.length - 1] : null
  return {
    replyOpacity: replyBtn ? getComputedStyle(replyBtn).opacity : 'none',
    lastClass: last ? last.className : ''
  }
})()`)
record('回复按钮默认隐藏（hover 才出现）', ui?.replyOpacity === '0', `opacity=${ui?.replyOpacity}`)
record('展开按钮在回复列表末尾', String(ui?.lastClass).includes('comment__expand'), ui?.lastClass)

const renderedBefore = await evaluate(
  `document.querySelectorAll('#comment-${rootId} .replies > .comment').length`)
record('折叠时只渲染 3 条回复', renderedBefore === 3, `rendered=${renderedBefore}`)

// 4.3 带 ?comment= 打开折叠中的回复 → 自动展开并高亮
// 高亮只持续约 3 秒，所以这里比常规等待早一些读取 class
await goto(`${base}/article/${article.slug}?comment=${adminReplyId}`, tokenOf(reader1.cookie), 2600)
const focused = await evaluate(`(() => {
  const el = document.getElementById('comment-${adminReplyId}')
  const rendered = document.querySelectorAll('#comment-${rootId} .replies > .comment').length
  return {
    exists: Boolean(el),
    highlighted: el ? el.classList.contains('comment--highlight') : false,
    rendered
  }
})()`)
record('折叠回复已自动展开', focused?.rendered === 6, `rendered=${focused?.rendered}`)
record('目标回复被高亮', focused?.exists && focused?.highlighted, JSON.stringify(focused))

// 4.4 通知实时同步：reader1 停在文章页，博主回复后 35 秒内应出现右上角提示
await goto(`${base}/article/${article.slug}`, tokenOf(reader1.cookie))
await call(`/api/v1/articles/${article.id}/comments`, {
  method: 'POST', headers: adminAuth,
  body: JSON.stringify({ content: `实时通知-${suffix}`, parentId: rootId })
})
let toastText = ''
for (let i = 0; i < 12; i++) {
  await new Promise((r) => setTimeout(r, 3000))
  toastText = await evaluate(`document.querySelector('.notif-toast')?.textContent ?? ''`)
  if (toastText) break
}
record('新通知 35 秒内自动出现（轮询）', Boolean(toastText), toastText.slice(0, 50))

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
