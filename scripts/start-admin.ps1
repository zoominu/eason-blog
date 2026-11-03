# 启动管理后台（Vite，端口 5174）
. "$PSScriptRoot\env.ps1"

$dir = Join-Path $Root 'blog-admin'
Push-Location $dir
try {
    if (-not (Test-Path 'node_modules')) {
        Write-Host '首次运行：安装后台依赖…' -ForegroundColor Cyan
        Invoke-Pnpm install --registry=https://registry.npmmirror.com
    }
    Write-Host '启动 blog-admin（http://localhost:5174）…' -ForegroundColor Cyan
    Invoke-Pnpm dev
} finally {
    Pop-Location
}
