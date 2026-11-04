/**
 * 复核 hover 范围：鼠标停在层级 2 评论上，只有它自己的「回复」变可见。
 * 用法：node check-hover.mjs <baseUrl> <slug> <level2Id> <level3Id>
 */
const [base, slug, l2, l3] = process.argv.slice(2)
const host = new URL(base).hostname

const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'reader2@eason.blog', password: 'Test123456' })
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

await send('Page.enable')
await send('Runtime.enable')
await send('Network.enable')
await send('Network.setCookie', { name: 'blog_token', value: token, domain: host, path: '/', httpOnly: true })
await send('Page.navigate', { url: `${base}/article/${slug}` })
await new Promise((r) => setTimeout(r, 5000))

const rectRes = await send('Runtime.evaluate', {
  expression: `(() => {
    const node = document.getElementById('comment-${l2}')
    if (!node) return null
    node.scrollIntoView({ block: 'center' })
    const target = node.querySelector('.comment__content')
    const r = target.getBoundingClientRect()
    return { x: r.left + 20, y: r.top + r.height / 2 }
  })()`,
  returnByValue: true
})
const rect = rectRes.result?.result?.value
if (rect) {
  await send('Input.dispatchMouseEvent', { type: 'mouseMoved', x: rect.x, y: rect.y, buttons: 0 })
  await new Promise((r) => setTimeout(r, 400))
}

const res = await send('Runtime.evaluate', {
  expression: `(() => {
    const own = document.querySelector('#comment-${l2} > .comment__actions .comment__reply')
    const child = document.querySelector('#comment-${l3} > .comment__actions .comment__reply')
    return { rect: ${JSON.stringify(rect)}, own: own ? getComputedStyle(own).opacity : 'none',
             child: child ? getComputedStyle(child).opacity : 'none' }
  })()`,
  returnByValue: true
})
const value = res.result?.result?.value
console.log(JSON.stringify(value))
process.exit(value?.own === '1' && value?.child === '0' ? 0 : 1)
