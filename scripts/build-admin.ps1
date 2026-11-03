# 构建管理后台静态资源到 blog-admin/dist
. "$PSScriptRoot\env.ps1"

Push-Location (Join-Path $Root 'blog-admin')
try {
    Invoke-Pnpm install --registry=https://registry.npmmirror.com --frozen-lockfile
    Invoke-Pnpm build
} finally {
    Pop-Location
}
