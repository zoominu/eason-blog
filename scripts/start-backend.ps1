# 启动后端（前台阻塞运行，Ctrl+C 停止）
. "$PSScriptRoot\env.ps1"

if (-not (Test-Port $env:DB_PORT)) {
    Write-Host 'MySQL 未启动，正在尝试启动…' -ForegroundColor Yellow
    & "$PSScriptRoot\start-mysql.ps1"
}

$mvn = Get-Mvn
Write-Host '启动 blog-server（http://127.0.0.1:8080）…' -ForegroundColor Cyan
Write-Host '提示：首次启动前请设置 BLOG_ADMIN_EMAIL / BLOG_ADMIN_PASSWORD 以创建管理员。' -ForegroundColor DarkGray

& $mvn -s $Settings -f (Join-Path $Root 'blog-server\pom.xml') spring-boot:run
