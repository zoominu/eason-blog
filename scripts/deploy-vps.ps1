<#
.SYNOPSIS
  把当前构建产物发布到 VPS（39.96.94.89）的源站端口部署。

.DESCRIPTION
  - 后端 jar -> /opt/eason-blog/app/app.jar
  - 前台 dist -> /var/www/eason-blog/
  - 后台 dist -> /var/www/eason-blog/admin/
  - 重启 eason-blog.service（Nginx 静态文件即时生效）

  用法：
    $env:VPS_PASSWORD='登录密码'
    powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy-vps.ps1 -SkipBuild
#>
param(
    [switch]$SkipBuild,
    [string]$VpsHost = $(if ($env:VPS_HOST) { $env:VPS_HOST } else { '39.96.94.89' }),
    [string]$User = 'root'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$jar = Join-Path $root 'blog-server/target/blog-server-1.0.0.jar'
$frontend = Join-Path $root 'blog-frontend/dist'
$admin = Join-Path $root 'blog-admin/dist'
foreach ($p in @($jar, $frontend, $admin)) {
    if (-not (Test-Path $p)) { throw "缺少构建产物：$p（请先构建）" }
}
if (-not $SkipBuild) {
    Write-Host '提示：本脚本不构建，使用现有 target/dist。需要最新代码请先构建。' -ForegroundColor Yellow
}
if (-not $env:VPS_PASSWORD) { throw '请先设置环境变量 VPS_PASSWORD' }

$askpass = Join-Path $env:TEMP ('askpass-' + [guid]::NewGuid().ToString('N') + '.cmd')
Set-Content -Path $askpass -Value "@echo off`r`necho $($env:VPS_PASSWORD)" -Encoding ASCII
$env:SSH_ASKPASS = $askpass
$env:SSH_ASKPASS_REQUIRE = 'force'
$env:DISPLAY = 'localhost:0'

$stage = Join-Path $root '.tools/vps-stage'
if (Test-Path $stage) { Remove-Item $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage | Out-Null
Copy-Item $jar (Join-Path $stage 'app.jar')
Copy-Item $frontend (Join-Path $stage 'frontend') -Recurse
Copy-Item $admin (Join-Path $stage 'admin') -Recurse

$bundle = Join-Path $root '.tools/vps-bundle.tar.gz'
if (Test-Path $bundle) { Remove-Item $bundle -Force }
& tar -czf $bundle -C $stage .
Write-Host ("上传包 {0:N1} MB ..." -f ((Get-Item $bundle).Length / 1MB))

& scp -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL $bundle "${User}@${VpsHost}:/tmp/vps-bundle.tar.gz"
if ($LASTEXITCODE -ne 0) { throw 'scp 上传失败' }

$remote = @'
set -e
rm -rf /tmp/vps-extract && mkdir -p /tmp/vps-extract
tar -xzf /tmp/vps-bundle.tar.gz -C /tmp/vps-extract
cp /tmp/vps-extract/app.jar /opt/eason-blog/app/app.jar
rm -rf /var/www/eason-blog/assets /var/www/eason-blog/index.html /var/www/eason-blog/favicon.svg
cp -r /tmp/vps-extract/frontend/. /var/www/eason-blog/
mkdir -p /var/www/eason-blog/admin
rm -rf /var/www/eason-blog/admin/assets /var/www/eason-blog/admin/index.html
cp -r /tmp/vps-extract/admin/. /var/www/eason-blog/admin/
chown -R easonblog:easonblog /opt/eason-blog/app
chown -R www-data:www-data /var/www/eason-blog
systemctl restart eason-blog
for i in $(seq 1 40); do
  code=$(curl -s -o /dev/null -w '%{http_code}' 'http://127.0.0.1:8080/api/v1/articles?size=5')
  [ "$code" = "200" ] && break
  sleep 2
done
systemctl is-active eason-blog
curl -s -o /dev/null -w 'home=%{http_code}\n' http://127.0.0.1:8080/
curl -s -o /dev/null -w 'api=%{http_code}\n' 'http://127.0.0.1:8080/api/v1/articles?size=5'
curl -s -o /dev/null -w 'admin=%{http_code}\n' http://127.0.0.1:8080/admin/
'@
$b64 = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($remote))

& ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=NUL "${User}@${VpsHost}" "echo $b64 | base64 -d | bash"
$code = $LASTEXITCODE
Remove-Item $askpass -Force -ErrorAction SilentlyContinue
if ($code -ne 0) { throw "远端部署失败（exit $code）" }
Write-Host '部署完成：http://39.96.94.89/（经雷池 80）'
