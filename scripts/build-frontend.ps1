# 构建读者前台静态资源到 blog-frontend/dist
. "$PSScriptRoot\env.ps1"

Push-Location (Join-Path $Root 'blog-frontend')
try {
    Invoke-Pnpm install --registry=https://registry.npmmirror.com --frozen-lockfile
    Invoke-Pnpm build
} finally {
    Pop-Location
}
