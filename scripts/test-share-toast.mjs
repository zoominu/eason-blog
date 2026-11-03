/**
 * 点击文章页「分享」，验证复制成功提示（Toast）。
 * 用法：node test-share-toast.mjs <debugPort> <articleUrl> <outPng>
 */
import { writeFileSync } from 'node:fs'

const [port, url, out] = process.argv.slice(2)
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
await send('Emulation.setFocusEmulationEnabled', { enabled: true })
await send('Browser.grantPermissions', {
  origin: new URL(url).origin,
  permissions: ['clipboardReadWrite', 'clipboardSanitizedWrite']
})
await send('Page.navigate', { url })
await new Promise((r) => setTimeout(r, 6000))
await send('Page.bringToFront')
await send('Emulation.setDeviceMetricsOverride', {
  width: 1440,
  height: 1000,
  deviceScaleFactor: 1,
  mobile: false
})

const click = await send('Runtime.evaluate', {
  expression: `(() => {
    // headless 的系统分享会挂起，这里模拟桌面浏览器（无 navigator.share）走复制路径
    try { Object.defineProperty(navigator, 'share', { value: undefined, configurable: true }) } catch (e) {}
    const btn = [...document.querySelectorAll('button.action')].find((b) => b.textContent.includes('分享'))
    if (!btn) return 'no-button'
    btn.click()
    return 'clicked'
  })()`,
  returnByValue: true,
  userGesture: true
})
console.log('click:', click.result?.result?.value)

await new Promise((r) => setTimeout(r, 700))
const toast = await send('Runtime.evaluate', {
  expression: `document.querySelector('.toast')?.textContent?.trim() ?? ''`,
  returnByValue: true
})
console.log('toast:', toast.result?.result?.value)

const shot = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false })
writeFileSync(out, Buffer.from(shot.result.data, 'base64'))
console.log(`saved ${out}`)
process.exit(0)
