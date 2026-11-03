/** 验证前端页面与 Vite 代理：GET / 应返回 index.html，GET /api/v1/site/config 应透传到后端。 */
const base = process.argv[2] ?? 'http://127.0.0.1:5173'

async function waitFor(url, timeoutMs = 60000) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      const r = await fetch(url)
      if (r.ok) return r
    } catch { /* retry */ }
    await new Promise((r) => setTimeout(r, 1000))
  }
  return null
}

const home = await waitFor(base + '/')
if (!home) {
  console.log('FAIL  dev server not reachable')
  process.exit(1)
}
const html = await home.text()
const hasApp = html.includes('id="app"') && html.includes('/src/main.ts')
console.log(`PASS  GET / -> ${home.status}, index.html=${hasApp}`)

const api = await fetch(base + '/api/v1/site/config')
const body = await api.json()
console.log(`${api.ok ? 'PASS' : 'FAIL'}  proxy GET /api/v1/site/config -> ${api.status}, title=${body?.data?.['site.title']}`)

const articles = await fetch(base + '/api/v1/articles?size=2')
const aBody = await articles.json()
console.log(`${articles.ok ? 'PASS' : 'FAIL'}  proxy GET /api/v1/articles -> ${articles.status}, total=${aBody?.data?.total}`)
