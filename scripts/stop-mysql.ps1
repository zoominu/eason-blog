# 停止本地 MySQL
. "$PSScriptRoot\env.ps1"

$admin = "$Mysql\bin\mysqladmin.exe"
if (Test-Path $admin) {
    & $admin -u root -h 127.0.0.1 --port=$env:DB_PORT shutdown 2>$null
}

Start-Sleep -Seconds 2
if (Test-Port $env:DB_PORT) {
    Get-Process mysqld -ErrorAction SilentlyContinue | Stop-Process -Force
    Write-Host 'MySQL 已强制停止。' -ForegroundColor Yellow
} else {
    Write-Host 'MySQL 已停止。' -ForegroundColor Green
}
