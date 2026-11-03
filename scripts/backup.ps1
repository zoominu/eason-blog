# 备份：数据库 + 上传目录（Windows 本机 / 开发环境用）
#
# 用法：
#   powershell -File scripts\backup.ps1 [-OutDir D:\backup]
#
# 生产环境请改用 cron + 异地存储（见 docs/DEPLOYMENT.md 第 6 节）。

param(
    [string]$OutDir = "$PSScriptRoot\..\..\backup",
    [string]$DbHost = "127.0.0.1",
    [int]$DbPort = 3306,
    [string]$DbName = "eason_blog",
    [string]$DbUser = "blog",
    [string]$DbPassword = $env:DB_PASSWORD
)

$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$mysqldump = Join-Path $root '.tools\rt\mysql-8.0.28-winx64\bin\mysqldump.exe'
if (-not (Test-Path $mysqldump)) { throw "缺少 mysqldump：$mysqldump" }
if (-not $DbPassword) { throw '请设置环境变量 DB_PASSWORD 或传 -DbPassword' }

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

# 1. 数据库
$sqlFile = Join-Path $OutDir "eason_blog-$stamp.sql"
& $mysqldump -h $DbHost -P $DbPort -u $DbUser "-p$DbPassword" `
    --single-transaction --routines --triggers --default-character-set=utf8mb4 `
    $DbName > $sqlFile
if ($LASTEXITCODE -ne 0) { throw '数据库备份失败' }
Write-Host "数据库已备份：$sqlFile" -ForegroundColor Green

# 2. 上传目录
$uploads = Join-Path $root 'blog-server\data\uploads'
if (Test-Path $uploads) {
    $zip = Join-Path $OutDir "uploads-$stamp.zip"
    Compress-Archive -Path "$uploads\*" -DestinationPath $zip -Force
    Write-Host "上传目录已备份：$zip" -ForegroundColor Green
} else {
    Write-Host '未找到上传目录，跳过' -ForegroundColor Yellow
}

# 3. 清理 14 天前的备份
Get-ChildItem $OutDir -File | Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-14) } |
    Remove-Item -Force
Write-Host '完成。' -ForegroundColor Green
