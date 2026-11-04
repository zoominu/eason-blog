/**
 * 评论 v4 验收：长内容换行/折叠、同页通知跳转、只高亮当前层级。
 * 用法：node verify-comment-v4.mjs [baseUrl]
 */
const base = process.argv[2] ?? 'http://192.168.245.131'
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
const r2Cookie = await login('reader2@eason.blog', 'Test123456')
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }
const r2Auth = { Cookie: r2Cookie, 'Content-Type': 'application/json' }

const articles = (await call('/api/v1/articles?size=20')).body.data.records
const article = articles.find((a) => a.slug === 'one-command-dev-environment')
record('取到测试文章', Boolean(article))

const suffix = Date.now().toString().slice(-5)
async function post(cookie, content, parentId = null) {
  const res = await call(`/api/v1/articles/${article.id}/comments`, {
    method: 'POST', headers: cookie,
    body: JSON.stringify({ content, parentId })
  })
  return res.body?.data?.id
}

const longText = `长评论-${suffix}-` + Array.from({ length: 10 }, (_, i) => `第${i + 1}行 ` + 'x'.repeat(40)).join('\n')
const longId = await post(r2Auth, longText)
record('创建超长评论', Boolean(longId), `id=${longId}`)

const shortId = await post(r2Auth, `同页通知根评论-${suffix}`)
record('创建根评论', Boolean(shortId), `id=${shortId}`)

const guestLong = `长留言-${suffix}-` + Array.from({ length: 10 }, (_, i) => `第${i + 1}行 ` + 'y'.repeat(40)).join('\n')
const guest = await call('/api/v1/guestbook', {
  method: 'POST', headers: r2Auth, body: JSON.stringify({ content: guestLong })
})
record('创建超长留言', guest.status === 200)

// ---------- 浏览器 ----------
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
const host = new URL(base).hostname
const goto = async (url, cookie, waitMs = 5000) => {
  await send('Network.setCookie', { name: 'blog_token', value: cookie.replace('blog_token=', ''), domain: host, path: '/', httpOnly: true })
  await send('Page.navigate', { url })
  await new Promise((r) => setTimeout(r, waitMs))
}

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Emulation.setDeviceMetricsOverride', { width: 1440, height: 1000, deviceScaleFactor: 1, mobile: false })

// 1) 长评论：自动换行 + 折叠 + 显示全部
// 新顶层评论在最后一页，用 ?comment= 让前端定位到它所在分页
await goto(`${base}/article/${article.slug}?comment=${longId}`, r2Cookie, 6000)
const longUi = await evaluate(`(() => {
  const node = document.getElementById('comment-${longId}')
  if (!node) return { error: 'no-node' }
  const content = node.querySelector('.comment__content')
  const more = node.querySelector('.comment__more')
  const style = content ? getComputedStyle(content) : null
  return {
    hasMore: Boolean(more),
    height: content ? Math.round(content.getBoundingClientRect().height) : 0,
    lineHeight: style ? parseFloat(style.lineHeight) : 0,
    overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth,
    moreText: more?.textContent?.trim() ?? ''
  }
})()`)
record('长评论出现「显示全部」按钮', longUi?.hasMore === true && longUi?.moreText === '显示全部', JSON.stringify(longUi))
record('长评论默认折叠为 6 行以内',
  longUi?.height > 0 && longUi.height <= longUi.lineHeight * 6.5,
  `height=${longUi?.height}, lineHeight=${longUi?.lineHeight}`)
record('长评论不产生横向滚动', (longUi?.overflowX ?? 99) <= 1, `overflowX=${longUi?.overflowX}`)

const expanded = await evaluate(`(async () => {
  const node = document.getElementById('comment-${longId}')
  const before = node.querySelector('.comment__content').getBoundingClientRect().height
  node.querySelector('.comment__more').click()
  await new Promise((r) => setTimeout(r, 300))
  const after = node.querySelector('.comment__content').getBoundingClientRect().height
  return { text: node.querySelector('.comment__more')?.textContent?.trim(),
           before: Math.round(before), after: Math.round(after) }
})()`, true)
record('点击后展开全文', expanded?.text === '收起' && expanded.after > expanded.before * 1.5,
  JSON.stringify(expanded))

// 2) 只高亮当前层级：父评论高亮时子评论背景保持透明
const reply = await post(adminAuth, `子回复-${suffix}`, shortId)
await goto(`${base}/article/${article.slug}?comment=${shortId}`, r2Cookie, 2600)
const highlight = await evaluate(`(() => {
  const root = document.getElementById('comment-${shortId}')
  const child = document.getElementById('comment-${reply}')
  if (!root || !child) return { error: 'missing' }
  const rootP = root.querySelector(':scope > .comment__content')
  const childP = child.querySelector(':scope > .comment__content')
  return {
    rootHighlighted: root.classList.contains('comment--highlight'),
    rootBg: getComputedStyle(rootP).backgroundColor,
    childBg: getComputedStyle(childP).backgroundColor
  }
})()`)
record('父评论高亮生效', highlight?.rootHighlighted === true, JSON.stringify(highlight))
record('子评论不被一起高亮', (highlight?.childBg ?? '').startsWith('rgba(0, 0, 0, 0'), `childBg=${highlight?.childBg}`)

// 3) 长留言：换行 + 折叠
await goto(`${base}/guestbook`, r2Cookie, 5000)
const guestUi = await evaluate(`(() => {
  const items = [...document.querySelectorAll('.item')]
  const item = items.find((el) => (el.textContent || '').includes('长留言-${suffix}'))
  if (!item) return { error: 'no-item' }
  const content = item.querySelector('.item__content')
  const more = item.querySelector('.item__more')
  return {
    hasMore: Boolean(more),
    clamped: content ? content.getBoundingClientRect().height < content.scrollHeight - 2 : false,
    overflowX: document.documentElement.scrollWidth - document.documentElement.clientWidth
  }
})()`)
record('长留言出现「显示全部」按钮', guestUi?.hasMore === true, JSON.stringify(guestUi))
record('长留言不产生横向滚动', (guestUi?.overflowX ?? 99) <= 1, `overflowX=${guestUi?.overflowX}`)

// 4) 同页点击通知：URL 只改 query 也要能定位高亮
await goto(`${base}/article/${article.slug}`, r2Cookie, 5000)
const newReply = await post(adminAuth, `同页通知回复-${suffix}`, shortId)
record('创建触发通知的回复', Boolean(newReply), `id=${newReply}`)

let toast = false
for (let i = 0; i < 12; i++) {
  await new Promise((r) => setTimeout(r, 3000))
  toast = await evaluate(`Boolean(document.querySelector('.notif-toast'))`)
  if (toast) break
}
record('新通知提示出现', toast === true)

const clicked = await evaluate(`(async () => {
  const el = document.querySelector('.notif-toast')
  if (!el) return { error: 'no-toast' }
  el.click()
  await new Promise((r) => setTimeout(r, 1800))
  const target = document.getElementById('comment-${newReply}')
  return { highlighted: target?.classList.contains('comment--highlight') ?? false,
           url: location.search }
})()`, true)
record('同页点击通知能跳转并高亮', clicked?.highlighted === true, JSON.stringify(clicked))

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
