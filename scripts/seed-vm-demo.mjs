/**
 * 为虚拟机测试环境准备演示用户与一张示例资源（1.jpg）。
 * 用法：node seed-vm-demo.mjs [baseUrl] [adminEmail] [adminPassword]
 */
const base = process.argv[2] ?? 'http://192.168.245.131'
const adminEmail = process.argv[3] ?? 'admin@eason.blog'
const adminPassword = process.argv[4] ?? 'Admin123456'

async function api(path, options = {}) {
  const res = await fetch(base + path, options)
  const text = await res.text()
  let body
  try { body = JSON.parse(text) } catch { body = text }
  return { status: res.status, body }
}

const loginRes = await fetch(base + '/api/v1/auth/login', {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: adminEmail, password: adminPassword })
})
const cookie = (loginRes.headers.get('set-cookie') ?? '').split(';')[0]
if (!cookie) throw new Error('管理员登录失败')
const auth = { Cookie: cookie, 'Content-Type': 'application/json' }

const USERS = [
  { email: 'reader1@eason.blog', nickname: '测试读者 A', password: 'Test123456' },
  { email: 'reader2@eason.blog', nickname: '测试读者 B', password: 'Test123456' }
]

for (const user of USERS) {
  const created = await api('/api/v1/admin/users', {
    method: 'POST', headers: auth, body: JSON.stringify(user)
  })
  if (created.status === 200) {
    console.log(`created ${user.email} -> id=${created.body?.data?.id}`)
    continue
  }
  if (created.status === 409) {
    const found = await api(`/api/v1/admin/users?keyword=${encodeURIComponent(user.email)}`, { headers: auth })
    const existing = found.body?.data?.records?.[0]
    if (existing) {
      await api(`/api/v1/admin/users/${existing.id}`, {
        method: 'PATCH', headers: auth, body: JSON.stringify({ nickname: user.nickname })
      })
      console.log(`exists ${user.email} -> id=${existing.id}（昵称已更新）`)
    }
    continue
  }
  throw new Error(`创建 ${user.email} 失败: ${created.status} ${JSON.stringify(created.body)}`)
}

// 一张 1x1 的 JPEG，作为「1.jpg」示例资源
const jpegBase64 =
  '/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////' +
  '2wBDAf//////////////////////////////////////////////////////////////////////////////////////wAARCAABAAEDASIAAhEBAxEB/8QAFQABAQAA' +
  'AAAAAAAAAAAAAAAAAAX/xAAUEAEAAAAAAAAAAAAAAAAAAAAA/9oADAMBAAIQAxAAAAF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABBQJ//8QAFBEBAAAA' +
  'AAAAAAAAAAAAAAAAAP/aAAgBAwEBPwF//8QAFBEBAAAAAAAAAAAAAAAAAAAAAP/aAAgBAgEBPwF//8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQAGPwJ//8QA' +
  'FBAQAQAAAAAAAAAAAAAAAAAA/9oACAEBAAE/IV//2gAMAwEAAgADAAAAEP/EABQRAQAAAAAAAAAAAAAAAAAAABD/2gAIAQMBAT8QH//EABQRAQAAAAAAAAAA' +
  'AAAAAAAAABD/2gAIAQIBAT8QH//EABQQAQAAAAAAAAAAAAAAAAAAABD/2gAIAQEAAT8QH//Z'

const existingResources = await api('/api/v1/admin/resources?keyword=1.jpg&size=50', { headers: auth })
const already = (existingResources.body?.data?.records ?? []).find((r) => r.originalName === '1.jpg')
if (already) {
  console.log(`resource exists: ${already.url}`)
} else {
  const bytes = Buffer.from(jpegBase64, 'base64')
  const form = new FormData()
  form.append('file', new Blob([bytes], { type: 'image/jpeg' }), '1.jpg')
  const upload = await fetch(base + '/api/v1/admin/resources/upload', {
    method: 'POST', headers: { Cookie: cookie }, body: form
  })
  const uploaded = await upload.json()
  console.log(`uploaded 1.jpg -> ${uploaded?.data?.url}`)
}

const users = await api('/api/v1/admin/users?size=100', { headers: auth })
console.log(`users total: ${users.body?.data?.total}`)
