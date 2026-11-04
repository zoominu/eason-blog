/**
 * 快速复核：文章页 ?comment=ID 是否定位并高亮（高亮约 3 秒）。
 * 用法：node check-highlight.mjs <baseUrl> <slug> <commentId>
 */
const [base, slug, commentId] = process.argv.slice(2)
const debugPort = 9333
const host = new URL(base).hostname

const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'reader1@eason.blog', password: 'Test123456' })
})
const token = (login.headers.get('set-cookie') ?? '').split(';')[0].replace('blog_token=', '')

const list = await (await fetch(`http://127.0.0.1:${debugPort}/json/list`)).json()
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
await send('Network.setCookie', {
  name: 'blog_token', value: token, domain: host, path: '/', httpOnly: true
})
await send('Page.navigate', { url: `${base}/article/${slug}?comment=${commentId}` })
await new Promise((r) => setTimeout(r, 2400))

const res = await send('Runtime.evaluate', {
  expression: `(() => {
    const el = document.getElementById('comment-${commentId}')
    return {
      exists: Boolean(el),
      highlighted: el ? el.classList.contains('comment--highlight') : false,
      scrollY: Math.round(window.scrollY)
    }
  })()`,
  returnByValue: true
})
console.log(JSON.stringify(res.result?.result?.value))
process.exit(res.result?.result?.value?.highlighted ? 0 : 1)
