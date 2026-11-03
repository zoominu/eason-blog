# 启动本地便携 MySQL（数据目录 .tools/mysql-data）
. "$PSScriptRoot\env.ps1"

if (Test-Port $env:DB_PORT) {
    Write-Host "MySQL 已在端口 $env:DB_PORT 运行。" -ForegroundColor Yellow
    exit 0
}

Assert-Path "$Mysql\bin\mysqld.exe" '请先准备 .tools/rt/mysql-8.0.28-winx64。'

if (-not (Test-Path $MysqlData)) {
    Write-Host '首次运行：初始化 MySQL 数据目录…' -ForegroundColor Cyan
    New-Item -ItemType Directory -Force -Path $MysqlData | Out-Null
    & "$Mysql\bin\mysqld.exe" --initialize-insecure --basedir="$Mysql" --datadir="$MysqlData" --console
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 初始化失败' }
}

$errLog = Join-Path $LogDir 'mysql-error.log'
Start-Process -FilePath "$Mysql\bin\mysqld.exe" `
    -ArgumentList "--basedir=$Mysql", "--datadir=$MysqlData", "--port=$($env:DB_PORT)",
                  '--bind-address=127.0.0.1', '--mysqlx=OFF', "--log-error=$errLog" `
    -WindowStyle Hidden

if (Wait-Port $env:DB_PORT 40) {
    Write-Host "MySQL 已启动：127.0.0.1:$($env:DB_PORT)（错误日志 $errLog）" -ForegroundColor Green
} else {
    Write-Host "MySQL 启动超时，请查看 $errLog" -ForegroundColor Red
    exit 1
}
