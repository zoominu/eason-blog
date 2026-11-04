<#
.SYNOPSIS
  构建并发布 Eason Blog 到 Linux 虚拟机（Ubuntu + systemd + Nginx）。

.DESCRIPTION
  流程：本地构建 jar / 前台 dist / 后台 dist(/admin/) → 打 tar 包 →
  scp 到目标机 → 执行远端 install.sh（幂等，会保留 /etc/eason-blog.env）。

.EXAMPLE
  $env:VM_PASSWORD = 'your-password'
  pwsh -File scripts/deploy-vm.ps1

.EXAMPLE
  # 跳过构建，直接用已有产物重新发布
  pwsh -File scripts/deploy-vm.ps1 -SkipBuild
#>
param(
    [string]$VmHost = "192.168.245.131",
    [string]$VmUser = "eason",
    [string]$VmPassword = $env:VM_PASSWORD,
    [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$bundle = Join-Path $root '.tools\vm-bundle'
$tar = Join-Path $root '.tools\vm-bundle.tar.gz'

if (-not $VmPassword) {
    # 密码只用于 ssh/scp，不写入仓库；建议用 $env:VM_PASSWORD 传入
    $secure = Read-Host "VM 密码 ($VmUser@$VmHost)" -AsSecureString
    $VmPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
        [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
if (-not $VmPassword) { throw '缺少 VM 密码：请设置 $env:VM_PASSWORD 或交互输入' }

function Get-Tool([string]$name, [string[]]$candidates) {
    foreach ($c in $candidates) { if ($c -and (Test-Path $c)) { return $c } }
    $cmd = Get-Command $name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    throw "找不到 $name，请先安装或确认 .tools 下的本地工具链存在"
}

if (-not $SkipBuild) {
    Write-Host '== 构建后端 jar ==' -ForegroundColor Cyan
    $javaHome = Join-Path $root '.tools\rt\jdk-21.0.13+9'
    $mvn = Get-Tool 'mvn' @((Join-Path $root '.tools\rt\apache-maven-3.9.16\bin\mvn.cmd'))
    if ($javaHome -and (Test-Path $javaHome)) { $env:JAVA_HOME = $javaHome }
    $settings = Join-Path $root '.tools\settings.xml'
    $mvnArgs = @('-f', (Join-Path $root 'blog-server\pom.xml'), '-B', '-q', 'clean', 'package', '-DskipTests')
    if (Test-Path $settings) { $mvnArgs = @('-s', $settings) + $mvnArgs }
    & $mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { throw '后端构建失败' }

    Write-Host '== 构建前台 ==' -ForegroundColor Cyan
    $pnpm = Get-Tool 'pnpm' @((Get-Command 'pnpm.cmd' -ErrorAction SilentlyContinue).Source)
    Push-Location (Join-Path $root 'blog-frontend')
    try { & $pnpm build; if ($LASTEXITCODE -ne 0) { throw '前台构建失败' } } finally { Pop-Location }

    Write-Host '== 构建后台（/admin/）==' -ForegroundColor Cyan
    Push-Location (Join-Path $root 'blog-admin')
    $env:ADMIN_BASE = '/admin/'
    try { & $pnpm build; if ($LASTEXITCODE -ne 0) { throw '后台构建失败' } } finally { Pop-Location }
}

Write-Host '== 组装发布包 ==' -ForegroundColor Cyan
if (Test-Path $bundle) { Remove-Item -Recurse -Force $bundle }
New-Item -ItemType Directory -Path $bundle | Out-Null
Copy-Item (Join-Path $root 'blog-server\target\blog-server-0.1.0-SNAPSHOT.jar') (Join-Path $bundle 'blog-server.jar')
Copy-Item -Recurse (Join-Path $root 'blog-frontend\dist') (Join-Path $bundle 'frontend')
Copy-Item -Recurse (Join-Path $root 'blog-admin\dist') (Join-Path $bundle 'admin')
Copy-Item (Join-Path $root 'deploy\vm\install.sh') $bundle
Copy-Item (Join-Path $root 'deploy\vm\eason-blog.service') $bundle
Copy-Item (Join-Path $root 'deploy\vm\eason-blog.conf') $bundle
if (Test-Path $tar) { Remove-Item -Force $tar }
Push-Location $bundle
try { tar -czf $tar . } finally { Pop-Location }

# OpenSSH 在 Windows 上不支持 stdin 传密码，用 SSH_ASKPASS 临时脚本
$askpass = Join-Path $env:TEMP 'eason-vm-askpass.cmd'
Set-Content -Path $askpass -Value "@echo $VmPassword" -Encoding Ascii
$env:SSH_ASKPASS = $askpass
$env:SSH_ASKPASS_REQUIRE = 'force'
$env:DISPLAY = '1'
$knownHosts = Join-Path $env:TEMP 'eason-vm-known_hosts'
$sshOpts = @(
    '-o', 'StrictHostKeyChecking=accept-new',
    '-o', "UserKnownHostsFile=$knownHosts",
    '-o', 'PreferredAuthentications=password',
    '-o', 'PubkeyAuthentication=no'
)
$target = "$VmUser@$VmHost"

try {
    Write-Host '== 上传发布包 ==' -ForegroundColor Cyan
    & scp @sshOpts $tar "${target}:/tmp/eason-blog-bundle.tar.gz"
    if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败' }

    Write-Host '== 远端安装 ==' -ForegroundColor Cyan
    $remote = @"
set -e
rm -rf /tmp/eason-blog-bundle && mkdir -p /tmp/eason-blog-bundle
tar -xzf /tmp/eason-blog-bundle.tar.gz -C /tmp/eason-blog-bundle
chmod +x /tmp/eason-blog-bundle/install.sh
echo '$VmPassword' | sudo -S -E bash /tmp/eason-blog-bundle/install.sh
"@
    & ssh @sshOpts -T $target $remote
    if ($LASTEXITCODE -ne 0) { throw '远端安装失败' }
}
finally {
    Remove-Item $askpass -Force -ErrorAction SilentlyContinue
}

Write-Host '== 部署后验收 ==' -ForegroundColor Cyan
try {
    $node = Get-Tool 'node' @()
    & $node (Join-Path $PSScriptRoot 'verify-vm.mjs') "http://$VmHost"
} catch {
    Write-Host "跳过验收（本机没有 node）：$($_.Exception.Message)" -ForegroundColor Yellow
}
Write-Host "完成：http://$VmHost/  后台 http://$VmHost/admin/" -ForegroundColor Green
