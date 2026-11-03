# 公共环境：定位便携工具链，供其他脚本 dot-source 使用。
# 用法：. "$PSScriptRoot\env.ps1"

$ErrorActionPreference = 'Stop'

$Root   = Split-Path -Parent $PSScriptRoot
$Tools  = Join-Path $Root '.tools'
$Jdk    = Join-Path $Tools 'rt\jdk-21.0.13+9'
$Maven  = Join-Path $Tools 'rt\apache-maven-3.9.16'
$Mysql  = Join-Path $Tools 'rt\mysql-8.0.28-winx64'
$MysqlData = Join-Path $Tools 'mysql-data'
$Settings  = Join-Path $Tools 'settings.xml'
$LogDir    = Join-Path $Tools 'logs'

$env:JAVA_HOME = $Jdk
$env:Path = "$Jdk\bin;$Maven\bin;" + $env:Path

# 数据库默认值（与 .env.example 一致；本机开发口令）
$env:DB_HOST = $env:DB_HOST ?? '127.0.0.1'
$env:DB_PORT = $env:DB_PORT ?? '3306'
$env:DB_NAME = $env:DB_NAME ?? 'eason_blog'
$env:DB_USER = $env:DB_USER ?? 'blog'
$env:DB_PASSWORD = $env:DB_PASSWORD ?? 'blog_dev_pwd'
$env:TEST_DB_NAME = $env:TEST_DB_NAME ?? 'eason_blog_test'

function Assert-Path($path, $hint) {
    if (-not (Test-Path $path)) {
        throw "缺少 $path。$hint"
    }
}

function Get-Mvn {
    Assert-Path "$Maven\bin\mvn.cmd" '请确认 .tools/rt/apache-maven-3.9.16 是否存在。'
    return "$Maven\bin\mvn.cmd"
}

function Get-MysqlBin($name) {
    $exe = "$Mysql\bin\$name.exe"
    Assert-Path $exe '请确认 .tools/rt/mysql-8.0.28-winx64 是否存在。'
    return $exe
}

function Test-Port($port) {
    try {
        return (Test-NetConnection -ComputerName 127.0.0.1 -Port $port -InformationLevel Quiet -WarningAction SilentlyContinue)
    } catch {
        return $false
    }
}

function Wait-Port($port, $seconds = 40) {
    for ($i = 0; $i -lt $seconds; $i++) {
        if (Test-Port $port) { return $true }
        Start-Sleep -Seconds 1
    }
    return $false
}

# Node：优先使用 PATH 中的 node；否则退回 DSH 运行时（本机环境）
function Get-Node {
    $cmd = Get-Command node -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = 'C:\Users\eason\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\node\bin\node.exe'
    if (Test-Path $fallback) { return $fallback }
    throw '未找到 Node.js。请安装 Node 20+ 或设置 DSH_NODE_PATH 后重试。'
}

function Get-Pnpm {
    $cmd = Get-Command pnpm -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $fallback = 'C:\Users\eason\.dsh\dsh-runtimes\dsh-primary-runtime\dependencies\pnpm\bin\pnpm.mjs'
    if (Test-Path $fallback) { return $fallback }
    throw '未找到 pnpm。可执行 npm install -g pnpm 后重试。'
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null

# 统一调用 pnpm：兼容 PATH 中的 pnpm 与本机 pnpm.mjs
function Invoke-Pnpm {
    param([Parameter(ValueFromRemainingArguments = $true)] $PnpmArgs)
    $pnpm = Get-Pnpm
    if ($pnpm.EndsWith('.mjs')) {
        $node = Get-Node
        & $node $pnpm @PnpmArgs
    } else {
        & $pnpm @PnpmArgs
    }
}
