/**
 * 用 CDP 在无头 Edge 里注入登录 Cookie 后截图（用于验证需要登录的后台页面）。
 * 用法：node shot.mjs <debugPort> <url> <cookieName> <cookieValue> <outFile>
 */
import { writeFileSync } from 'node:fs'

const [port, url, cookieName, cookieValue, out] = process.argv.slice(2)
const base = `http://127.0.0.1:${port}`

async function waitForTargets(timeoutMs = 15000) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      const list = await (await fetch(`${base}/json/list`)).json()
      const page = list.find((t) => t.type === 'page' && t.webSocketDebuggerUrl)
      if (page) return page
    } catch { /* browser not ready */ }
    await new Promise((r) => setTimeout(r, 500))
  }
  throw new Error('no page target')
}

const page = await waitForTargets()
const ws = new WebSocket(page.webSocketDebuggerUrl)
let seq = 0
const pending = new Map()

ws.addEventListener('message', (event) => {
  const msg = JSON.parse(event.data)
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
await send('Network.enable')
await send('Network.setCookie', {
  name: cookieName,
  value: cookieValue,
  // 按目标站点设置域名：既支持 127.0.0.1，也支持虚拟机 IP
  domain: new URL(url).hostname,
  path: '/',
  httpOnly: true
})
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440,
  height: 1000,
  deviceScaleFactor: 1,
  mobile: false
})
await send('Page.navigate', { url })
await new Promise((r) => setTimeout(r, 6000))

// 可选第 6 个参数：滚动到包含该文本的元素（例如设置页的分组标题）
const scrollToText = process.argv[7]
if (scrollToText) {
  await send('Runtime.evaluate', {
    expression: `(() => {
      const target = [...document.querySelectorAll('*')]
        .find((el) => el.children.length === 0 && (el.textContent || '').includes(${JSON.stringify(scrollToText)}))
      if (target) target.scrollIntoView({ block: 'start' })
      window.scrollBy(0, -90)
      return Boolean(target)
    })()`,
    returnByValue: true
  })
  await new Promise((r) => setTimeout(r, 700))
}

const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
if (!shot.result?.data) {
  console.error('no screenshot data', JSON.stringify(shot).slice(0, 300))
  process.exit(1)
}
writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
console.log(`saved ${out}`)
process.exit(0)
