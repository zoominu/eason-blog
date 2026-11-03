# 运行后端测试（需要 MySQL 与 eason_blog_test 库）
. "$PSScriptRoot\env.ps1"

if (-not (Test-Port $env:DB_PORT)) {
    & "$PSScriptRoot\start-mysql.ps1"
}

$mysql = Get-MysqlBin 'mysql'
& $mysql -u root -h 127.0.0.1 --port=$env:DB_PORT -e "CREATE DATABASE IF NOT EXISTS $($env:TEST_DB_NAME) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;" 2>$null

$mvn = Get-Mvn
& $mvn -s $Settings -f (Join-Path $Root 'blog-server\pom.xml') test
exit $LASTEXITCODE
