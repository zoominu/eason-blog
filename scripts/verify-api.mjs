/**
 * 端到端接口验证脚本（不依赖浏览器）：验证健康检查、公开接口、认证、权限与管理员初始化。
 * 用法：node verify-api.mjs [baseUrl]
 */
const base = process.argv[2] ?? 'http://127.0.0.1:8080'

const results = []
function record(name, ok, detail) {
  results.push({ name, ok, detail })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`)
}

async function call(path, options = {}) {
  const res = await fetch(base + path, options)
  const text = await res.text()
  let body = null
  try { body = JSON.parse(text) } catch { body = text }
  return { status: res.status, body, headers: res.headers }
}

async function waitForHealth(timeoutMs = 90000) {
  const started = Date.now()
  while (Date.now() - started < timeoutMs) {
    try {
      const r = await fetch(base + '/actuator/health')
      if (r.ok) return true
    } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 1500))
  }
  return false
}

if (!(await waitForHealth())) {
  console.error('backend is not up')
  process.exit(1)
}
record('GET /actuator/health', true, 'UP')

const health = await call('/actuator/health')
record('health body', health.body?.status === 'UP', JSON.stringify(health.body))

const config = await call('/api/v1/site/config')
record('GET /site/config 200', config.status === 200)
record('site.title present', Boolean(config.body?.data?.['site.title']), config.body?.data?.['site.title'])

const articles = await call('/api/v1/articles?size=3')
record('GET /articles 200', articles.status === 200)
record('articles seeded', (articles.body?.data?.total ?? 0) >= 1, `total=${articles.body?.data?.total}`)
const firstSlug = articles.body?.data?.records?.[0]?.slug

if (firstSlug) {
  const detail = await call('/api/v1/articles/' + encodeURIComponent(firstSlug))
  record('GET /articles/{slug} 200', detail.status === 200 && Boolean(detail.body?.data?.content),
    `slug=${firstSlug}, viewCount=${detail.body?.data?.viewCount}`)
}

const cats = await call('/api/v1/categories')
record('GET /categories 200', cats.status === 200 && Array.isArray(cats.body?.data), `count=${cats.body?.data?.length}`)

const tags = await call('/api/v1/tags')
record('GET /tags 200', tags.status === 200, `count=${tags.body?.data?.length}`)

const stats = await call('/api/v1/site/statistics')
record('GET /site/statistics 200', stats.status === 200, JSON.stringify(stats.body?.data))

const guestbook = await call('/api/v1/guestbook')
record('GET /guestbook 200', guestbook.status === 200)

// 匿名访问管理端必须 401
const anonAdmin = await call('/api/v1/admin/dashboard')
record('anonymous /admin/dashboard -> 401', anonAdmin.status === 401, `status=${anonAdmin.status}`)

// 注册普通用户
const email = `verify-${Date.now()}@test.local`
const register = await call('/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: '验证用户' })
})
record('POST /auth/register 200', register.status === 200, register.body?.data?.email)

// 登录普通用户
const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123' })
})
const userCookie = (login.headers.get('set-cookie') ?? '').split(';')[0]
record('POST /auth/login 200 + cookie', login.status === 200 && userCookie.startsWith('blog_token='))

const me = await call('/api/v1/auth/me', { headers: { Cookie: userCookie } })
record('GET /auth/me with cookie', me.status === 200 && me.body?.data?.email === email)

const userAdmin = await call('/api/v1/admin/dashboard', { headers: { Cookie: userCookie } })
record('USER /admin/dashboard -> 403', userAdmin.status === 403, `status=${userAdmin.status}`)

// 管理员登录（由 BLOG_ADMIN_EMAIL/PASSWORD 初始化）
const adminLogin = await fetch(base + '/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@eason.blog', password: 'Admin123456' })
})
const adminCookie = (adminLogin.headers.get('set-cookie') ?? '').split(';')[0]
record('admin login 200', adminLogin.status === 200, `cookie=${adminCookie ? 'yes' : 'no'}`)

if (adminCookie) {
  const dash = await call('/api/v1/admin/dashboard', { headers: { Cookie: adminCookie } })
  record('SUPER_ADMIN /admin/dashboard 200', dash.status === 200,
    `published=${dash.body?.data?.publishedArticles}, views=${dash.body?.data?.totalViews}`)
}

// 退出登录应清除 Cookie
const logout = await fetch(base + '/api/v1/auth/logout', { method: 'POST', headers: { Cookie: userCookie } })
record('POST /auth/logout 200', logout.status === 200)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
