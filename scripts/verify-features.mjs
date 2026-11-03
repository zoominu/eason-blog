/**
 * 功能补齐验证：RSS、评论分页、留言回复、找回密码、邮箱验证、归档。
 * 用法：node verify-features.mjs [baseUrl]
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
  return { status: res.status, body, headers: res.headers }
}

async function login(email, password) {
  const res = await fetch(base + '/api/v1/auth/login', {
    method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  })
  return { status: res.status, cookie: (res.headers.get('set-cookie') ?? '').split(';')[0] }
}

const admin = await login('admin@eason.blog', 'Admin123456')
record('管理员登录', admin.status === 200)

// ---------- RSS ----------
const rss = await call('/rss.xml')
record('RSS 可访问', rss.status === 200 && String(rss.body).includes('<rss version="2.0"'))
record('RSS 含文章条目', String(rss.body).includes('<item>'))
record('RSS 声明内容类型', (rss.headers.get('content-type') ?? '').includes('rss+xml'),
  rss.headers.get('content-type'))

// ---------- 归档 ----------
const archive = await call('/api/v1/archive')
record('归档接口', archive.status === 200 && Array.isArray(archive.body?.data),
  `months=${archive.body?.data?.length}`)

// ---------- 评论分页 ----------
const list = await call('/api/v1/articles?size=5')
const slug = list.body?.data?.records?.[0]?.slug
const comments = await call(`/api/v1/articles/${slug}/comments?page=1&size=2`)
record('评论接口返回分页结构',
  comments.status === 200 && typeof comments.body?.data?.total === 'number'
  && Array.isArray(comments.body?.data?.records),
  `total=${comments.body?.data?.total}`)

// ---------- 留言回复 ----------
const suffix = Date.now().toString().slice(-6)
const email = `feature-${suffix}@test.local`
await call('/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: '功能用户' })
})
const user = await login(email, 'Passw0rd123')
const content = `功能验证留言-${suffix}`
await call('/api/v1/guestbook', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Cookie: user.cookie },
  body: JSON.stringify({ content })
})

const pending = await call('/api/v1/admin/guestbook?status=0&size=50', { headers: { Cookie: admin.cookie } })
const target = (pending.body?.data?.records ?? []).find((g) => g.content === content)
record('后台能看到待审核留言', Boolean(target), `id=${target?.id}`)

const reply = await call(`/api/v1/admin/guestbook/${target.id}/reply`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json', Cookie: admin.cookie },
  body: JSON.stringify({ reply: '感谢留言，已收到。' })
})
record('博主回复留言', reply.status === 200)

const publicGuestbook = await call('/api/v1/guestbook?size=50')
const repliedItem = (publicGuestbook.body?.data?.records ?? []).find((g) => g.content === content)
record('回复后留言公开可见且带回复',
  Boolean(repliedItem) && repliedItem.reply === '感谢留言，已收到。')

const emptyReply = await call(`/api/v1/admin/guestbook/${target.id}/reply`, {
  method: 'PATCH',
  headers: { 'Content-Type': 'application/json', Cookie: admin.cookie },
  body: JSON.stringify({ reply: '   ' })
})
record('空回复被拒绝（400）', emptyReply.status === 400)

// ---------- 找回密码 ----------
const forgot = await call('/api/v1/auth/forgot-password', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email })
})
record('找回密码接口（已注册邮箱）', forgot.status === 200)

const forgotUnknown = await call('/api/v1/auth/forgot-password', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: `nobody-${suffix}@test.local` })
})
record('未注册邮箱也返回 200（防枚举）', forgotUnknown.status === 200)

const badReset = await call('/api/v1/auth/reset-password', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ token: 'not-a-token', newPassword: 'NewPass123' })
})
record('无效重置 token 返回 400', badReset.status === 400, `status=${badReset.status}`)

const weakReset = await call('/api/v1/auth/reset-password', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ token: 'x', newPassword: '12345678' })
})
record('弱密码被拒绝（422）', weakReset.status === 422, `status=${weakReset.status}`)

// ---------- 邮箱验证 ----------
const badVerify = await call('/api/v1/auth/verify-email', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ token: 'not-a-token' })
})
record('无效验证 token 返回 400', badVerify.status === 400)

const me = await call('/api/v1/auth/me', { headers: { Cookie: user.cookie } })
record('/auth/me 返回 emailVerified 字段', typeof me.body?.data?.emailVerified === 'boolean',
  `emailVerified=${me.body?.data?.emailVerified}`)

const resend = await call('/api/v1/auth/resend-verification', {
  method: 'POST', headers: { Cookie: user.cookie }
})
record('重新发送验证邮件', resend.status === 200)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
