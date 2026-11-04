/**
 * 评论 v3 验收：隐藏占位、删除父评论、目录跳转偏移、跳转可重复高亮。
 * 用法：node verify-comment-v3.mjs [baseUrl]
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
  return (res.headers.get('set-cookie') ?? '').split(';')[0]
}

const adminCookie = await login('admin@eason.blog', 'Admin123456')
const r1Cookie = await login('reader1@eason.blog', 'Test123456')
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }
const r1Auth = { Cookie: r1Cookie, 'Content-Type': 'application/json' }
record('账号登录', Boolean(adminCookie && r1Cookie))

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

// 1) 拒绝父评论 → 占位 + 回复保留
const root = await post(r1Auth, `占位测试-${suffix}`)
const reply = await post(adminAuth, `占位回复-${suffix}`, root)
await call(`/api/v1/admin/comments/${root}/reject`, { method: 'POST', headers: adminAuth })

const list = await call(`/api/v1/articles/${article.slug}/comments?size=50`)
const rootNode = (list.body?.data?.records ?? []).find((c) => c.id === root)
record('父评论被拒绝后显示占位', rootNode?.hidden === true && rootNode?.content === '当前评论已被隐藏',
  JSON.stringify({ hidden: rootNode?.hidden, content: rootNode?.content }))
record('父评论下的回复仍可见', (rootNode?.replies ?? []).some((c) => c.id === reply))

// 2) 删除父评论 → 子评论一起删除
const root2 = await post(r1Auth, `删除测试-${suffix}`)
const reply2 = await post(adminAuth, `删除回复-${suffix}`, root2)
const del = await call(`/api/v1/admin/comments/${root2}`, { method: 'DELETE', headers: adminAuth })
record('删除父评论连带删除回复', del.status === 200 && del.body?.data?.deleted === 2, JSON.stringify(del.body?.data))

const afterDelete = await call(`/api/v1/articles/${article.slug}/comments?size=50`)
const texts = JSON.stringify(afterDelete.body)
record('删除后公开列表不再出现', !texts.includes(`删除测试-${suffix}`) && !texts.includes(`删除回复-${suffix}`))

// ---------- 浏览器 ----------
const page = (await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json())
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

// 2.1 页面渲染占位
await goto(`${base}/article/${article.slug}`, r1Cookie)
const placeholder = await evaluate(`(() => {
  const el = document.getElementById('comment-${root}')
  return {
    exists: Boolean(el),
    text: el?.innerText?.includes('当前评论已被隐藏') ?? false,
    replyVisible: Boolean(el?.querySelector('#comment-${reply}')),
    replyButton: el ? el.querySelectorAll(':scope > .comment__actions .comment__reply').length : -1
  }
})()`)
record('页面显示「当前评论已被隐藏」', placeholder?.text === true, JSON.stringify(placeholder))
record('占位评论没有回复按钮', placeholder?.replyButton === 0)
record('占位评论下回复仍渲染', placeholder?.replyVisible === true)

// 2.2 目录跳转不被导航栏遮挡
const toc = await evaluate(`(async () => {
  const item = document.querySelector('.toc__item')
  if (!item) return { error: 'no-toc' }
  const label = item.textContent.trim()
  item.click()
  await new Promise((r) => setTimeout(r, 1200))
  const heading = [...document.querySelectorAll('.prose-blog h2, .prose-blog h3')]
    .find((h) => h.textContent.trim() === label)
  return { label, top: heading ? Math.round(heading.getBoundingClientRect().top) : -999 }
})()`, true)
record('目录跳转后标题未被导航遮挡', toc?.top > 64, JSON.stringify(toc))

// 2.3 跳转高亮可以重复触发（离开再回来仍高亮）
await goto(`${base}/article/${article.slug}?comment=${reply}`, r1Cookie, 2600)
const first = await evaluate(`(() => {
  const el = document.getElementById('comment-${reply}')
  return { exists: Boolean(el), highlighted: el?.classList.contains('comment--highlight') ?? false }
})()`)
record('第一次跳转高亮', first?.highlighted === true, JSON.stringify(first))

await goto(`${base}/article/${article.slug}`, r1Cookie, 2200)
await goto(`${base}/article/${article.slug}?comment=${reply}`, r1Cookie, 2600)
const second = await evaluate(`(() => {
  const el = document.getElementById('comment-${reply}')
  return { highlighted: el?.classList.contains('comment--highlight') ?? false, url: location.search }
})()`)
record('再次跳转仍然高亮（未被去重逻辑吞掉）', second?.highlighted === true, JSON.stringify(second))

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
