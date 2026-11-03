# 创建业务库、测试库与应用账号（幂等，可重复执行）
. "$PSScriptRoot\env.ps1"

if (-not (Test-Port $env:DB_PORT)) {
    throw "MySQL 未启动，请先运行 scripts/start-mysql.ps1"
}

$mysql = Get-MysqlBin 'mysql'
$sql = @"
CREATE DATABASE IF NOT EXISTS $($env:DB_NAME) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE IF NOT EXISTS $($env:TEST_DB_NAME) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '$($env:DB_USER)'@'%' IDENTIFIED BY '$($env:DB_PASSWORD)';
ALTER USER '$($env:DB_USER)'@'%' IDENTIFIED BY '$($env:DB_PASSWORD)';
GRANT ALL PRIVILEGES ON $($env:DB_NAME).* TO '$($env:DB_USER)'@'%';
GRANT ALL PRIVILEGES ON $($env:TEST_DB_NAME).* TO '$($env:DB_USER)'@'%';
FLUSH PRIVILEGES;
"@

$sql | & $mysql -u root -h 127.0.0.1 --port=$env:DB_PORT
if ($LASTEXITCODE -ne 0) { throw '创建数据库失败' }

& $mysql -u $env:DB_USER -p$env:DB_PASSWORD -h 127.0.0.1 --port=$env:DB_PORT -e "SHOW DATABASES;" 2>$null
Write-Host "数据库就绪：$($env:DB_NAME) / $($env:TEST_DB_NAME)，账号 $($env:DB_USER)" -ForegroundColor Green
