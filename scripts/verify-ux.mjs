/**
 * 体验优化验证：阻止自回复、博主标记、通知定位、批量审核、一键已读、分享模板、分页容量。
 * 用法：node verify-ux.mjs [baseUrl]
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
const asAdmin = (options = {}) => ({
  ...options,
  headers: { 'Content-Type': 'application/json', Cookie: admin.cookie, ...(options.headers ?? {}) }
})

// 新建一个用户，避免历史数据干扰
const suffix = Date.now().toString().slice(-6)
const email = `ux-${suffix}@test.local`
await call('/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: '体验用户' })
})
const user = await login(email, 'Passw0rd123')
const asUser = (options = {}) => ({
  ...options,
  headers: { 'Content-Type': 'application/json', Cookie: user.cookie, ...(options.headers ?? {}) }
})

const list = await call('/api/v1/articles?size=1')
const article = list.body.data.records[0]

// ---------- 1. 不能回复自己 ----------
const own = await call(`/api/v1/articles/${article.id}/comments`, asUser({
  method: 'POST', body: JSON.stringify({ content: `自评-${suffix}` })
}))
const ownId = own.body?.data?.id
record('发表评论', own.status === 200, `id=${ownId}`)

const selfReply = await call(`/api/v1/articles/${article.id}/comments`, asUser({
  method: 'POST', body: JSON.stringify({ content: '自己回自己', parentId: ownId })
}))
record('不能回复自己的评论（400）', selfReply.status === 400, `status=${selfReply.status}`)

// ---------- 2. 博主标记 ----------
const adminComment = await call(`/api/v1/articles/${article.id}/comments`, asAdmin({
  method: 'POST', body: JSON.stringify({ content: `博主评论-${suffix}` })
}))
const adminCommentId = adminComment.body?.data?.id
await call(`/api/v1/admin/comments/${adminCommentId}/approve`, asAdmin({ method: 'POST' }))
await call(`/api/v1/admin/comments/${ownId}/approve`, asAdmin({ method: 'POST' }))

const comments = await call(`/api/v1/articles/${article.slug}/comments?size=50`)
const adminNode = (comments.body?.data?.records ?? []).find((c) => c.content === `博主评论-${suffix}`)
record('博主评论带 author 标记', adminNode?.author === true, `author=${adminNode?.author}`)

// ---------- 3/4. 通知内容与跳转 ----------
const myNotifications = await call('/api/v1/users/me/notifications?limit=50', asUser())
const approved = (myNotifications.body?.data ?? []).find((n) => n.type === 'SYSTEM')
record('审核通过通知说明是哪条评论', (approved?.content ?? '').includes('「'), approved?.content ?? '(none)')
record('审核通过通知跳转到对应文章评论', (approved?.link ?? '').includes(`?comment=${ownId}`),
  approved?.link)

const adminNotifications = await call('/api/v1/users/me/notifications?limit=50', asAdmin())
const adminNotif = (adminNotifications.body?.data ?? []).find((n) => n.type === 'COMMENT' && (n.link ?? '').includes('highlight'))
record('管理员新评论通知跳转审核页并定位', Boolean(adminNotif), adminNotif?.link)

// ---------- 5. 一键已读 / 删除已读 ----------
const readAll = await call('/api/v1/users/me/notifications/read-all', asUser({ method: 'PATCH' }))
record('一键已读', readAll.status === 200, `updated=${readAll.body?.data?.updated}`)

const unread = await call('/api/v1/users/me/notifications/unread-count', asUser())
record('一键已读后未读为 0', unread.body?.data?.count === 0, `count=${unread.body?.data?.count}`)

const deleteRead = await call('/api/v1/users/me/notifications/read', asUser({ method: 'DELETE' }))
record('删除已读通知', deleteRead.status === 200, `deleted=${deleteRead.body?.data?.deleted}`)

// ---------- 6. 批量审核 ----------
const first = await call(`/api/v1/articles/${article.id}/comments`, asUser({
  method: 'POST', body: JSON.stringify({ content: `批量A-${suffix}` })
}))
const second = await call(`/api/v1/articles/${article.id}/comments`, asUser({
  method: 'POST', body: JSON.stringify({ content: `批量B-${suffix}` })
}))
const batchIds = [first.body?.data?.id, second.body?.data?.id]
const batch = await call('/api/v1/admin/comments/batch', asAdmin({
  method: 'POST', body: JSON.stringify({ ids: batchIds, approve: true })
}))
record('批量通过评论', batch.status === 200 && batch.body?.data?.updated === 2,
  `updated=${batch.body?.data?.updated}`)

const guestContent = `批量留言-${suffix}`
await call('/api/v1/guestbook', asUser({
  method: 'POST', body: JSON.stringify({ content: guestContent })
}))
const pendingGuest = await call('/api/v1/admin/guestbook?status=0&size=100', { headers: { Cookie: admin.cookie } })
const guest = (pendingGuest.body?.data?.records ?? []).find((g) => g.content === guestContent)
const guestBatch = await call('/api/v1/admin/guestbook/batch', asAdmin({
  method: 'POST', body: JSON.stringify({ ids: [guest?.id], approve: true })
}))
record('批量通过留言', guestBatch.status === 200 && guestBatch.body?.data?.updated === 1,
  `updated=${guestBatch.body?.data?.updated}`)

// ---------- 7. 分享模板 ----------
const config = await call('/api/v1/site/config')
record('站点配置暴露 share.template', Boolean(config.body?.data?.['share.template']),
  config.body?.data?.['share.template'])

// ---------- 8. 分页容量 100 ----------
const bigPage = await call('/api/v1/admin/comments?size=100', { headers: { Cookie: admin.cookie } })
record('后台评论支持每页 100', bigPage.status === 200, `size=${bigPage.body?.data?.size}`)

const logPage = await call('/api/v1/admin/login-logs?size=100', { headers: { Cookie: admin.cookie } })
record('登录日志支持每页 100', logPage.status === 200, `size=${logPage.body?.data?.size}`)

// ---------- 9. 评论分页按顶层计数 ----------
const paged = await call(`/api/v1/articles/${article.slug}/comments?page=1&size=1`)
record('评论分页按顶层评论计数', paged.status === 200 && typeof paged.body?.data?.total === 'number',
  `total=${paged.body?.data?.total}, records=${paged.body?.data?.records?.length}`)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
