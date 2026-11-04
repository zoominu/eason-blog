/**
 * 部署后验收：前台、后台、API、登录、SPA 回退、RSS。
 * 用法：node verify-vm.mjs [baseUrl]
 */
const base = process.argv[2] ?? 'http://192.168.245.131'
const results = []

function record(name, ok, detail = '') {
  results.push({ name, ok })
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? '  -> ' + detail : ''}`)
}

async function get(path, options = {}) {
  const res = await fetch(base + path, options)
  const text = await res.text()
  return { status: res.status, text, headers: res.headers, setCookie: res.headers.get('set-cookie') }
}

// 前台
const home = await get('/')
record('前台首页 200', home.status === 200 && home.text.includes('<div id="app">'), `status=${home.status}`)

const assetMatch = home.text.match(/src="(\/assets\/[^"]+)"/)
if (assetMatch) {
  const asset = await get(assetMatch[1])
  record('前台静态资源可访问', asset.status === 200, `${assetMatch[1]} (${asset.status})`)
}

// 后台（/admin/ 子路径）
const admin = await get('/admin/')
record('后台首页 200', admin.status === 200, `status=${admin.status}`)
record('后台资源前缀为 /admin/', admin.text.includes('/admin/assets/'))

const adminDeep = await get('/admin/comments')
record('后台深链接回退到 SPA', adminDeep.status === 200 && adminDeep.text.includes('/admin/assets/'))

// API
const articles = await get('/api/v1/articles?size=5')
let articleBody
try { articleBody = JSON.parse(articles.text) } catch { articleBody = null }
record('公开文章接口', articles.status === 200 && Array.isArray(articleBody?.data?.records),
  `total=${articleBody?.data?.total}`)

const site = await get('/api/v1/site/config')
record('站点配置接口', site.status === 200)

// 登录（验证 Cookie 与数据库迁移）
const login = await fetch(base + '/api/v1/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: 'admin@eason.blog', password: 'Admin123456' })
})
const cookie = (login.headers.get('set-cookie') ?? '').split(';')[0]
record('管理员登录', login.status === 200 && cookie.startsWith('blog_token='))

const me = await get('/api/v1/auth/me', { headers: { Cookie: cookie } })
record('/auth/me 返回管理员', me.status === 200 && me.text.includes('SUPER_ADMIN'))

const adminUsers = await get('/api/v1/admin/users?size=5', { headers: { Cookie: cookie } })
record('后台用户接口（角色播种成功）', adminUsers.status === 200)

// 上传链路：写文件 → 通过 nginx /uploads 读回 → 删除
const form = new FormData()
form.append('file', new Blob(['eason blog vm smoke test'], { type: 'text/plain' }), 'smoke.txt')
const upload = await fetch(base + '/api/v1/admin/resources/upload', {
  method: 'POST',
  headers: { Cookie: cookie },
  body: form
})
const uploaded = await upload.json().catch(() => null)
record('资源上传', upload.status === 200 && Boolean(uploaded?.data?.url), uploaded?.data?.url)

if (uploaded?.data?.url) {
  const file = await get(uploaded.data.url)
  record('上传文件可通过 /uploads 访问', file.status === 200, `status=${file.status}`)
  const removed = await fetch(base + '/api/v1/admin/resources/' + uploaded.data.id, {
    method: 'DELETE',
    headers: { Cookie: cookie }
  })
  record('删除上传文件', removed.status === 200, `status=${removed.status}`)
}

// RSS / sitemap
const rss = await get('/rss.xml')
record('RSS 可访问', rss.status === 200 && rss.text.includes('<rss version="2.0"'))

const sitemap = await get('/sitemap.xml')
record('sitemap 可访问', sitemap.status === 200 && sitemap.text.includes('<urlset'))

// 健康检查不应对外暴露
const health = await get('/actuator/health')
record('actuator 不对外暴露', health.status === 404 || health.status === 401, `status=${health.status}`)

// 后台静态资源
const adminAssetMatch = admin.text.match(/src="(\/admin\/assets\/[^"]+)"/)
if (adminAssetMatch) {
  const adminAsset = await get(adminAssetMatch[1])
  record('后台静态资源可访问', adminAsset.status === 200, `${adminAssetMatch[1]} (${adminAsset.status})`)
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
