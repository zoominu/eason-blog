/**
 * 审计报告修复验证（P0/P1/P2 关键项）。
 * 用法：node verify-audit.mjs [baseUrl]
 */
const base = process.argv[2] ?? 'http://127.0.0.1:8080'
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
  return {
    status: res.status,
    cookie: (res.headers.get('set-cookie') ?? '').split(';')[0]
  }
}

// ---------- P1-9 列表标签 ----------
const list = await call('/api/v1/articles?size=10')
const withTags = (list.body?.data?.records ?? []).filter((a) => (a.tags ?? []).length > 0)
record('列表接口返回标签（不再恒为空）', withTags.length > 0,
  `tagged=${withTags.length}/${list.body?.data?.records?.length}`)

const slug = list.body?.data?.records?.[0]?.slug
const detail = await call(`/api/v1/articles/${slug}`)
record('详情返回 categorySlug', Boolean(detail.body?.data?.categorySlug))
record('详情返回 tagSlugs', Array.isArray(detail.body?.data?.tagSlugs))
const detailData = detail.body?.data ?? {}
record('详情返回上一篇/下一篇字段',
  'prevSlug' in detailData || 'nextSlug' in detailData,
  `prev=${detailData.prevSlug ?? '-'}, next=${detailData.nextSlug ?? '-'}`)

// ---------- P1-8 分类详情 ----------
const catSlug = detail.body?.data?.categorySlug
const category = await call(`/api/v1/categories/${catSlug}`)
record('分类详情接口可用', category.status === 200 && category.body?.data?.name === detail.body?.data?.categoryName,
  `name=${category.body?.data?.name}`)

const missingCat = await call('/api/v1/categories/not-exists-xyz')
record('分类不存在返回 404', missingCat.status === 404)

// ---------- 归档 / sitemap / robots ----------
const archive = await call('/api/v1/archive')
record('归档接口', archive.status === 200 && Array.isArray(archive.body?.data),
  `months=${archive.body?.data?.length}`)

const sitemap = await call('/sitemap.xml')
record('sitemap.xml 可访问', sitemap.status === 200 && String(sitemap.body).includes('<urlset'))

const robots = await call('/robots.txt')
record('robots.txt 可访问', robots.status === 200 && String(robots.body).includes('Sitemap:'))

// ---------- P2-3 改密后旧 token 失效 ----------
const suffix = Date.now().toString().slice(-6)
const email = `audit-${suffix}@test.local`
await call('/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: '审计用户' })
})
const session = await login(email, 'Passw0rd123')
record('新用户登录', session.status === 200 && session.cookie.startsWith('blog_token='))

const meBefore = await call('/api/v1/auth/me', { headers: { Cookie: session.cookie } })
record('改密前可访问 /auth/me', meBefore.status === 200)

const changed = await call('/api/v1/users/me/password', {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json', Cookie: session.cookie },
  body: JSON.stringify({ oldPassword: 'Passw0rd123', newPassword: 'Passw0rd456' })
})
record('修改密码成功', changed.status === 200, `status=${changed.status}`)

const meAfter = await call('/api/v1/auth/me', { headers: { Cookie: session.cookie } })
record('旧 token 立即失效（401）', meAfter.status === 401, `status=${meAfter.status}`)

const relogin = await login(email, 'Passw0rd456')
record('新密码可重新登录', relogin.status === 200)

// ---------- P2-5 专栏文章绑定 ----------
const adminSession = await login('admin@eason.blog', 'Admin123456')
const adminCookie = adminSession.cookie
const columns = await call('/api/v1/admin/columns', { headers: { Cookie: adminCookie } })
const columnId = columns.body?.data?.[0]?.id
record('专栏返回 sort 字段', columns.body?.data?.[0]?.sort !== undefined, `sort=${columns.body?.data?.[0]?.sort}`)

const bound = await call(`/api/v1/admin/columns/${columnId}/articles`, { headers: { Cookie: adminCookie } })
record('后台可读取专栏文章', bound.status === 200 && Array.isArray(bound.body?.data),
  `count=${bound.body?.data?.length}`)

const setAgain = await call(`/api/v1/admin/columns/${columnId}/articles`, {
  method: 'PUT',
  headers: { 'Content-Type': 'application/json', Cookie: adminCookie },
  body: JSON.stringify({ articleIds: (bound.body?.data ?? []).map((a) => a.id) })
})
record('可保存专栏文章顺序（幂等）', setAgain.status === 200)

// ---------- 公开接口不再暴露草稿 ----------
const drafts = await call('/api/v1/articles?size=50')
const hasDraft = (drafts.body?.data?.records ?? []).some((a) => a.slug.startsWith('draft-'))
record('公开列表不含草稿', !hasDraft)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
