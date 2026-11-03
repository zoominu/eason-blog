/**
 * P3 + P4 端到端验证：搜索、专栏文章、评论审核、点赞幂等、收藏、留言、通知。
 * 用法：node verify-p3p4.mjs [baseUrl]
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
  return (res.headers.get('set-cookie') ?? '').split(';')[0]
}

const adminCookie = await login('admin@eason.blog', 'Admin123456')
record('管理员登录', adminCookie.startsWith('blog_token='))

const suffix = Date.now().toString().slice(-6)
const email = `p3p4-${suffix}@test.local`
await call('/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: 'P3P4 用户' })
})
const userCookie = await login(email, 'Passw0rd123')
record('普通用户登录', userCookie.startsWith('blog_token='))

const asUser = (options = {}) => ({
  ...options,
  headers: { 'Content-Type': 'application/json', Cookie: userCookie, ...(options.headers ?? {}) }
})
const asAdmin = (options = {}) => ({
  ...options,
  headers: { 'Content-Type': 'application/json', Cookie: adminCookie, ...(options.headers ?? {}) }
})

// ---------- P3 搜索 ----------
const search = await call('/api/v1/search?keyword=' + encodeURIComponent('索引'))
record('搜索「索引」有结果', search.status === 200 && search.body.data.total >= 1,
  `total=${search.body?.data?.total}`)

const emptySearch = await call('/api/v1/search?keyword=' + encodeURIComponent('不存在的关键词xyz'))
record('搜索无结果返回空分页', emptySearch.status === 200 && emptySearch.body.data.total === 0)

// ---------- P3 专栏文章 ----------
const column = await call('/api/v1/columns/build-blog-from-scratch/articles')
record('专栏文章按顺序返回', column.status === 200 && column.body.data.length >= 1,
  `count=${column.body?.data?.length}`)

const articleSlug = column.body?.data?.[0]?.slug
const articleId = column.body?.data?.[0]?.id
record('拿到测试文章', Boolean(articleSlug && articleId), `slug=${articleSlug}`)

// ---------- P4 评论 ----------
const post = await call(`/api/v1/articles/${articleId}/comments`, asUser({
  method: 'POST', body: JSON.stringify({ content: '这是一条 P3P4 验证评论' })
}))
const commentId = post.body?.data?.id
record('登录用户可发表评论', post.status === 200 && Boolean(commentId), `id=${commentId}`)

const beforeApprove = await call(`/api/v1/articles/${articleSlug}/comments`)
const visibleBefore = (beforeApprove.body?.data ?? []).some((c) => c.id === commentId)
record('待审核评论对访客不可见', !visibleBefore)

const approve = await call(`/api/v1/admin/comments/${commentId}/approve`, asAdmin({ method: 'POST' }))
record('管理员审核通过', approve.status === 200)

const afterApprove = await call(`/api/v1/articles/${articleSlug}/comments`)
const visibleAfter = (afterApprove.body?.data ?? []).some((c) => c.id === commentId)
record('审核后评论可见', visibleAfter)

const anonComment = await call(`/api/v1/articles/${articleId}/comments`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ content: '匿名' })
})
record('未登录不能评论（401）', anonComment.status === 401)

// ---------- P4 点赞 / 收藏 ----------
const like1 = await call(`/api/v1/articles/${articleId}/like`, asUser({ method: 'POST' }))
record('点赞成功', like1.status === 200 && like1.body.data.liked === true)

const like2 = await call(`/api/v1/articles/${articleId}/like`, asUser({ method: 'POST' }))
record('重复点赞幂等', like2.body.data.likeCount === like1.body.data.likeCount,
  `count=${like2.body?.data?.likeCount}`)

const fav = await call(`/api/v1/articles/${articleId}/favorite`, asUser({ method: 'POST' }))
record('收藏成功', fav.status === 200 && fav.body.data.favorited === true)

const favList = await call('/api/v1/users/me/favorites', { headers: { Cookie: userCookie } })
record('收藏出现在个人中心', favList.body?.data?.total >= 1)

// ---------- P4 留言 ----------
const gb = await call('/api/v1/guestbook', asUser({
  method: 'POST', body: JSON.stringify({ content: '这是一条 P3P4 验证留言' })
}))
record('登录用户可留言', gb.status === 200)

const gbBefore = await call('/api/v1/guestbook')
const gbVisibleBefore = (gbBefore.body?.data?.records ?? []).some((g) => g.content.includes('P3P4'))
record('待审核留言不可见', !gbVisibleBefore)

const pendingGb = await call('/api/v1/admin/guestbook?status=0&size=50', { headers: { Cookie: adminCookie } })
const gbId = (pendingGb.body?.data?.records ?? []).find((g) => g.content.includes('P3P4'))?.id
const gbApprove = await call(`/api/v1/admin/guestbook/${gbId}/approve`, asAdmin({ method: 'POST' }))
record('管理员通过留言', gbApprove.status === 200)

const gbAfter = await call('/api/v1/guestbook')
record('通过后留言可见', (gbAfter.body?.data?.records ?? []).some((g) => g.content.includes('P3P4')))

// ---------- P4 通知 ----------
const notices = await call('/api/v1/users/me/notifications', { headers: { Cookie: adminCookie } })
const hasCommentNotice = (notices.body?.data ?? []).some((n) => n.type === 'COMMENT')
record('评论触发管理员通知', hasCommentNotice)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
