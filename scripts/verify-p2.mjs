/**
 * P2 端到端验证：分类/标签/文章 CRUD、发布下线、回收恢复、删除引用校验、权限。
 * 用法：node verify-p2.mjs [baseUrl]
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
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  })
  const cookie = (res.headers.get('set-cookie') ?? '').split(';')[0]
  return { status: res.status, cookie }
}

const admin = await login('admin@eason.blog', 'Admin123456')
record('管理员登录', admin.status === 200 && admin.cookie.startsWith('blog_token='))

const asAdmin = (options = {}) => ({
  ...options,
  headers: { 'Content-Type': 'application/json', Cookie: admin.cookie, ...(options.headers ?? {}) }
})

const suffix = Date.now().toString().slice(-6)

// 分类
const cat = await call('/api/v1/admin/categories', asAdmin({
  method: 'POST',
  body: JSON.stringify({ name: `验证分类${suffix}`, description: 'e2e' })
}))
const categoryId = cat.body?.data?.id
record('新建分类', cat.status === 200 && Boolean(categoryId), `id=${categoryId}`)

// 标签
const tag = await call('/api/v1/admin/tags', asAdmin({
  method: 'POST',
  body: JSON.stringify({ name: `验证标签${suffix}` })
}))
const tagId = tag.body?.data?.id
record('新建标签', tag.status === 200 && Boolean(tagId), `id=${tagId}`)

// 文章（草稿）
const slug = `p2-e2e-${suffix}`
const created = await call('/api/v1/admin/articles', asAdmin({
  method: 'POST',
  body: JSON.stringify({
    title: 'P2 端到端验证文章',
    slug,
    categoryId,
    content: '## 小标题\n\n这是 **P2** 验证正文。\n\n```java\nint a = 1;\n```',
    summary: '验证摘要',
    tagIds: [tagId],
    status: 0
  })
}))
const articleId = created.body?.data?.id
record('新建文章（草稿）', created.status === 200 && Boolean(articleId), `id=${articleId}`)

const draftPublic = await call(`/api/v1/articles/${slug}`)
record('草稿在前台不可见', draftPublic.status === 404, `status=${draftPublic.status}`)

// 发布
const published = await call(`/api/v1/admin/articles/${articleId}/publish`, asAdmin({ method: 'POST' }))
record('发布文章', published.status === 200)

const publicDetail = await call(`/api/v1/articles/${slug}`)
record('发布后前台可见', publicDetail.status === 200 && publicDetail.body?.data?.title === 'P2 端到端验证文章')
record('正文 Markdown 正确返回', (publicDetail.body?.data?.content ?? '').includes('## 小标题'))

// 编辑
const updated = await call(`/api/v1/admin/articles/${articleId}`, asAdmin({
  method: 'PATCH',
  body: JSON.stringify({
    title: 'P2 端到端验证文章（已改）',
    slug,
    categoryId,
    content: '## 改过的正文',
    tagIds: [tagId],
    isTop: 1
  })
}))
record('更新文章', updated.status === 200)
const afterUpdate = await call(`/api/v1/articles/${slug}`)
record('更新后前台同步', (afterUpdate.body?.data?.title ?? '').endsWith('（已改）'))

// 分类有文章时不能删
const catDelete = await call(`/api/v1/admin/categories/${categoryId}`, asAdmin({ method: 'DELETE' }))
record('分类被引用时禁止删除（409）', catDelete.status === 409, `status=${catDelete.status}`)

// 标签被引用时不能删
const tagDelete = await call(`/api/v1/admin/tags/${tagId}`, asAdmin({ method: 'DELETE' }))
record('标签被引用时禁止删除（409）', tagDelete.status === 409, `status=${tagDelete.status}`)

// 下线
const offline = await call(`/api/v1/admin/articles/${articleId}/offline`, asAdmin({ method: 'POST' }))
record('下线文章', offline.status === 200)
const offlinePublic = await call(`/api/v1/articles/${slug}`)
record('下线后前台不可见', offlinePublic.status === 404)

// 回收 + 恢复
await call(`/api/v1/admin/articles/${articleId}`, asAdmin({ method: 'DELETE' }))
const inRecycle = await call('/api/v1/admin/articles?status=3&size=50', { headers: { Cookie: admin.cookie } })
const recycleIds = (recycleIn => recycleIn.body?.data?.records?.map((r) => r.id) ?? [])(inRecycle)
record('删除进入回收站', recycleIds.includes(articleId))

const restored = await call(`/api/v1/admin/articles/${articleId}/restore`, asAdmin({ method: 'POST' }))
record('从回收站恢复', restored.status === 200)

// 权限：普通用户
const userEmail = `p2-user-${suffix}@test.local`
await call('/api/v1/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: userEmail, password: 'Passw0rd123', nickname: 'P2 用户' })
})
const normal = await login(userEmail, 'Passw0rd123')
const forbidden = await call('/api/v1/admin/articles', { headers: { Cookie: normal.cookie } })
record('普通用户访问管理端 403', forbidden.status === 403, `status=${forbidden.status}`)

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
