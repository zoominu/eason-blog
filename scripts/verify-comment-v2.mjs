/**
 * 评论交互 v2 验收：发表后立即展示、只高亮一次、四级上限、hover 范围、分页跳转。
 * 用法：node verify-comment-v2.mjs [baseUrl]
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
const r2Cookie = await login('reader2@eason.blog', 'Test123456')
const adminAuth = { Cookie: adminCookie, 'Content-Type': 'application/json' }
const r1Auth = { Cookie: r1Cookie, 'Content-Type': 'application/json' }
const r2Auth = { Cookie: r2Cookie, 'Content-Type': 'application/json' }
record('账号登录', Boolean(adminCookie && r1Cookie && r2Cookie))

const articles = (await call('/api/v1/articles?size=20')).body.data.records
const articleA = articles.find((a) => a.slug === 'one-command-dev-environment')
const articleB = articles.find((a) => a.slug === 'why-java-21-for-side-projects')
record('测试文章存在', Boolean(articleA && articleB))

const suffix = Date.now().toString().slice(-5)
async function post(cookie, articleId, content, parentId = null) {
  const res = await call(`/api/v1/articles/${articleId}/comments`, {
    method: 'POST', headers: cookie,
    body: JSON.stringify({ content, parentId })
  })
  return res.body?.data?.id
}

// 四级评论链
const l1 = await post(r1Auth, articleA.id, `v2-层级1-${suffix}`)
const l2 = await post(adminAuth, articleA.id, `v2-层级2-${suffix}`, l1)
const l3 = await post(r1Auth, articleA.id, `v2-层级3-${suffix}`, l2)
const l4 = await post(adminAuth, articleA.id, `v2-层级4-${suffix}`, l3)
record('四级评论创建成功', Boolean(l1 && l2 && l3 && l4), `${l1}/${l2}/${l3}/${l4}`)

// 第三级再回复应被拒绝
const depthReject = await call(`/api/v1/articles/${articleA.id}/comments`, {
  method: 'POST', headers: r1Auth,
  body: JSON.stringify({ content: '第五级', parentId: l4 })
})
record('第五级评论被拒绝（400）', depthReject.status === 400, `status=${depthReject.status}`)

// 为分页准备：给 B 文章补 2 条顶层评论（共 12 条 → 2 页）
await post(adminAuth, articleB.id, `分页补充1-${suffix}`)
await post(adminAuth, articleB.id, `分页补充2-${suffix}`)
const bComments = await call(`/api/v1/articles/${articleB.slug}/comments?page=1&size=10`)
record('文章 B 达到 2 页', bComments.body?.data?.pages === 2, `total=${bComments.body?.data?.total}`)

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

// 1) 发表后立即展示（不刷新）
const uiContent = `即时展示-${suffix}`
await goto(`${base}/article/${articleA.slug}`, r2Cookie)
const submitted = await evaluate(`(async () => {
  const textarea = document.querySelector('#comment-form textarea')
  if (!textarea) return { error: 'no-textarea' }
  textarea.value = ${JSON.stringify(uiContent)}
  textarea.dispatchEvent(new Event('input', { bubbles: true }))
  await new Promise((r) => setTimeout(r, 150))
  const btn = [...document.querySelectorAll('#comment-form button')].find((b) => b.textContent.includes('发表评论'))
  if (!btn) return { error: 'no-button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 2500))
  return {
    visible: document.body.innerText.includes(${JSON.stringify(uiContent)}),
    highlighted: Boolean([...document.querySelectorAll('.comment--highlight')].length),
    textareaEmpty: (document.querySelector('#comment-form textarea')?.value ?? '') === ''
  }
})()`, true)
record('评论发表后立即展示（无需刷新）', submitted?.visible === true, JSON.stringify(submitted))
record('新评论自动高亮一次', submitted?.highlighted === true)

// 2) 高亮只一次：跳转高亮后清理 URL，刷新不再高亮
await goto(`${base}/article/${articleA.slug}?comment=${l4}`, r1Cookie, 2600)
const firstHighlight = await evaluate(`(() => {
  const el = document.getElementById('comment-${l4}')
  return { exists: Boolean(el), highlighted: el?.classList.contains('comment--highlight') ?? false,
           url: location.search }
})()`)
record('通知跳转时高亮', firstHighlight?.highlighted === true, JSON.stringify(firstHighlight))

await new Promise((r) => setTimeout(r, 4200))
const afterHighlight = await evaluate(`(() => ({
  highlighted: Boolean(document.querySelector('#comment-${l4}.comment--highlight')),
  url: location.search
}))()`)
record('高亮后清除 URL 参数', afterHighlight?.url === '', `url=${afterHighlight?.url}`)

await send('Page.reload')
await new Promise((r) => setTimeout(r, 3200))
const afterReload = await evaluate(
  `Boolean(document.querySelector('#comment-${l4}.comment--highlight'))`)
record('刷新后不再高亮', afterReload === false)

// 3) 四级不再有回复按钮（用 reader2 视角：l3 是 reader1 的评论，不属于自己才显示按钮）
await goto(`${base}/article/${articleA.slug}`, r2Cookie, 4500)
const depthUi = await evaluate(`(() => ({
  level4Reply: document.querySelectorAll('#comment-${l4} > .comment__actions .comment__reply').length,
  level3Reply: document.querySelectorAll('#comment-${l3} > .comment__actions .comment__reply').length
}))()`)
record('第 4 级没有回复按钮', depthUi?.level4Reply === 0, JSON.stringify(depthUi))
record('第 3 级仍有回复按钮', depthUi?.level3Reply === 1)

// 4) hover 只影响当前这条评论（先把目标滚进视口，否则鼠标事件落在视口外）
const rect = await evaluate(`(() => {
  const el = document.getElementsByClassName('comment__content')
  const target = [...el].find((node) => (node.textContent || '').includes('层级2-${suffix}'))
  if (!target) return null
  target.scrollIntoView({ block: 'center' })
  const r = target.getBoundingClientRect()
  return { x: r.left + 20, y: r.top + r.height / 2 }
})()`)
if (rect) {
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rect.x, y: rect.y, buttons: 0 })
  await new Promise((r) => setTimeout(r, 350))
}
const hover = await evaluate(`(() => {
  const own = document.querySelector('#comment-${l2} > .comment__actions .comment__reply')
  const child = document.querySelector('#comment-${l3} > .comment__actions .comment__reply')
  return { own: own ? getComputedStyle(own).opacity : 'none',
           child: child ? getComputedStyle(child).opacity : 'none' }
})()`)
record('hover 只显示当前评论的回复按钮', hover?.own === '1' && hover?.child === '0', JSON.stringify(hover))

// 5) 分页：第 2 页可切换
await goto(`${base}/article/${articleB.slug}`, r1Cookie)
const pager = await evaluate(`(() => {
  const pager = document.querySelector('.pager')
  return { exists: Boolean(pager), text: pager?.textContent?.replace(/\\s+/g, ' ').trim() ?? '' }
})()`)
record('评论超过 10 条出现分页', pager?.exists === true, pager?.text)

const page2 = await evaluate(`(async () => {
  const buttons = [...document.querySelectorAll('.pager__btn')]
  const target = buttons.find((b) => b.textContent.trim() === '2')
  if (!target) return { error: 'no-page2' }
  target.click()
  await new Promise((r) => setTimeout(r, 1800))
  return { url: location.search, active: document.querySelector('.pager__btn--active')?.textContent?.trim() ?? '' }
})()`, true)
record('可以翻到第 2 页', page2?.active === '2', JSON.stringify(page2))

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
