/**
 * P5 + P6 端到端验证：用户管理、角色权限、资源上传、日志审计、系统设置、限流。
 * 用法：node verify-p5.mjs [baseUrl]
 */
import { readFileSync, writeFileSync, mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

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
  return { status: res.status, cookie: (res.headers.get('set-cookie') ?? '').split(';')[0] }
}

const admin = await login('admin@eason.blog', 'Admin123456')
record('管理员登录', admin.status === 200)

const asAdmin = (options = {}) => ({
  ...options,
  headers: { Cookie: admin.cookie, ...(options.headers ?? {}) }
})

// ---------- 用户管理 ----------
const users = await call('/api/v1/admin/users?size=5', asAdmin())
record('用户列表', users.status === 200 && users.body.data.total >= 1, `total=${users.body?.data?.total}`)

const suffix = Date.now().toString().slice(-6)
const email = `p5-user-${suffix}@test.local`
const registered = await call('/api/v1/auth/register', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'Passw0rd123', nickname: 'P5 用户' })
})
record('注册测试用户', registered.status === 200)

const found = await call(`/api/v1/admin/users?keyword=${encodeURIComponent(email)}`, asAdmin())
const target = found.body?.data?.records?.[0]
record('按邮箱搜索到用户', Boolean(target), `id=${target?.id}`)

const disabled = await call(`/api/v1/admin/users/${target.id}/disable`, asAdmin({ method: 'POST' }))
record('禁用用户', disabled.status === 200)

const blockedLogin = await login(email, 'Passw0rd123')
record('禁用后无法登录（403）', blockedLogin.status === 403, `status=${blockedLogin.status}`)

const enabled = await call(`/api/v1/admin/users/${target.id}/enable`, asAdmin({ method: 'POST' }))
record('重新启用用户', enabled.status === 200)

const relogin = await login(email, 'Passw0rd123')
record('启用后可登录', relogin.status === 200)

const selfDisable = await call('/api/v1/admin/users/1/disable', asAdmin({ method: 'POST' }))
record('禁止禁用自己（400）', selfDisable.status === 400, `status=${selfDisable.status}`)

// ---------- 角色权限 ----------
const roles = await call('/api/v1/admin/roles', asAdmin())
record('角色列表', roles.status === 200 && roles.body.data.length >= 2)

const perms = await call('/api/v1/admin/permissions', asAdmin())
record('权限点列表', perms.status === 200 && perms.body.data.length >= 5)

const roleId = roles.body.data[0].id
const originalPerms = roles.body.data[0].permissionIds
const updatedPerms = await call(`/api/v1/admin/roles/${roleId}/permissions`, asAdmin({
  method: 'PATCH', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ permissionIds: originalPerms })
}))
record('保存角色权限（幂等）', updatedPerms.status === 200)

// ---------- 资源上传 ----------
const dir = mkdtempSync(join(tmpdir(), 'p5-'))
const filePath = join(dir, 'hello.txt')
writeFileSync(filePath, 'hello eason blog')

const form = new FormData()
form.append('file', new Blob([readFileSync(filePath)], { type: 'text/plain' }), 'hello.txt')
const upload = await fetch(base + '/api/v1/admin/resources/upload', {
  method: 'POST', headers: { Cookie: admin.cookie }, body: form
})
const uploadBody = await upload.json()
record('上传文本文件', upload.status === 200 && Boolean(uploadBody.data?.url), uploadBody?.data?.url)

const resourceId = uploadBody.data?.id
const fetchFile = await fetch(base + uploadBody.data.url)
record('上传后可访问（/uploads）', fetchFile.status === 200, `status=${fetchFile.status}`)

const badExt = new FormData()
badExt.append('file', new Blob(['#!/bin/sh'], { type: 'application/x-sh' }), 'evil.sh')
const badUpload = await fetch(base + '/api/v1/admin/resources/upload', {
  method: 'POST', headers: { Cookie: admin.cookie }, body: badExt
})
record('拒绝可执行脚本上传（400）', badUpload.status === 400, `status=${badUpload.status}`)

const listRes = await call('/api/v1/admin/resources?size=50', asAdmin())
record('资源列表', listRes.status === 200 && listRes.body.data.total >= 1)

// ---------- 系统设置 ----------
const settings = await call('/api/v1/admin/settings', asAdmin())
record('读取系统设置', settings.status === 200 && settings.body.data.length >= 5)
const siteTitle = settings.body.data.find((s) => s.key === 'site.title')
record('包含 site.title', Boolean(siteTitle), siteTitle?.value)

const updateSetting = await call('/api/v1/admin/settings', asAdmin({
  method: 'PATCH', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 'site.title': siteTitle.value })
}))
record('保存系统设置', updateSetting.status === 200)

const invalidSetting = await call('/api/v1/admin/settings', asAdmin({
  method: 'PATCH', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ 'not.exist.key': 'x' })
}))
record('拒绝未知配置项（400）', invalidSetting.status === 400)

// ---------- 日志 ----------
const loginLogs = await call('/api/v1/admin/login-logs?size=5', asAdmin())
record('登录日志列表', loginLogs.status === 200 && loginLogs.body.data.total >= 1)

const opLogs = await call('/api/v1/admin/operation-logs?size=5', asAdmin())
record('操作日志已记录', opLogs.status === 200 && opLogs.body.data.total >= 1,
  `total=${opLogs.body?.data?.total}`)

// ---------- 限流 ----------
let limited = false
for (let i = 0; i < 12; i++) {
  const r = await login('admin@eason.blog', 'wrong-password-' + i)
  if (r.status === 429) { limited = true; break }
}
record('登录失败触发限流（429）', limited)

// ---------- 清理 ----------
if (resourceId) {
  await call(`/api/v1/admin/resources/${resourceId}`, asAdmin({ method: 'DELETE' }))
}

const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length === 0 ? 0 : 1)
