/**
 * 在无头浏览器里点击文章页的「复制」按钮，验证复制逻辑（含非安全上下文回退）。
 * 用法：node test-copy.mjs <debugPort> <articleUrl>
 */
const [port, url] = process.argv.slice(2)
const base = `http://127.0.0.1:${port}`

const list = await (await fetch(`${base}/json/list`)).json()
const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
if (!page) throw new Error('no page target')

const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pending = new Map()
ws.addEventListener('message', (e) => {
  const msg = JSON.parse(e.data)
  if (msg.id && pending.has(msg.id)) {
    pending.get(msg.id)(msg)
    pending.delete(msg.id)
  }
})
await new Promise((resolve, reject) => {
  ws.addEventListener('open', resolve)
  ws.addEventListener('error', reject)
})

function send(method, params = {}) {
  const id = ++seq
  return new Promise((resolve) => {
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
}

await send('Page.enable')
await send('Runtime.enable')
// 无头浏览器默认不聚焦、拒绝剪贴板写入：开启焦点模拟并授权，
// 以便验证 Clipboard API 成功路径（不授权时会走回退逻辑，显示「已全选」）。
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Browser.grantPermissions', {
  origin: new URL(url).origin,
  permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite']
})
await send('Page.navigate', { url })
await new Promise((r) => setTimeout(r, 6000))
await send('Page.bringToFront')

const evaluate = async (expression, userGesture = false) => {
  const res = await send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true,
    userGesture
  })
  return res.result?.result?.value
}

const count = await evaluate("document.querySelectorAll('.code-copy').length")
console.log('copy buttons:', count)

const result = await evaluate(`(async () => {
  const btn = document.querySelector('.code-copy')
  if (!btn) return { ok: false, reason: 'no button' }
  btn.click()
  await new Promise((r) => setTimeout(r, 500))
  return {
    ok: btn.textContent !== '复制失败' && btn.textContent !== '复制',
    text: btn.textContent,
    secure: window.isSecureContext,
    hasClipboard: Boolean(navigator.clipboard)
  }
})()`, true)

console.log('click result:', JSON.stringify(result))
process.exit(result && result.ok ? 0 : 1)
