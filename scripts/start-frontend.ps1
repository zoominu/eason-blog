# 启动读者前台（Vite，端口 5173）
. "$PSScriptRoot\env.ps1"

$dir = Join-Path $Root 'blog-frontend'
Push-Location $dir
try {
    if (-not (Test-Path 'node_modules')) {
        Write-Host '首次运行：安装前端依赖…' -ForegroundColor Cyan
        Invoke-Pnpm install --registry=https://registry.npmmirror.com
    }
    Write-Host '启动 blog-frontend（http://localhost:5173）…' -ForegroundColor Cyan
    Invoke-Pnpm dev
} finally {
    Pop-Location
}
