# 把仓库推送到远程（GitHub 等），使用仓库内便携 Git + 独立 SSH 密钥
#
# 用法：
#   powershell -File scripts\push-github.ps1 -RepoUrl git@github.com:<user>/<repo>.git
#
# 前置条件：
#   1. 已在 GitHub 创建空仓库（不要勾选 README / .gitignore / license）；
#   2. 已把 .tools/ssh/github_key.pub 的内容加到 GitHub → Settings → SSH and GPG keys。

param(
    [Parameter(Mandatory = $true)]
    [string]$RepoUrl,

    [string]$Branch = 'main'
)

$ErrorActionPreference = 'Stop'

$Root = Split-Path -Parent $PSScriptRoot
$git = Join-Path $Root '.tools\git\cmd\git.exe'
$ssh = 'C:\WINDOWS\System32\OpenSSH\ssh.exe'
$key = Join-Path $Root '.tools\ssh\id_ed25519'

if (-not (Test-Path $git)) { throw "缺少便携 Git：$git" }
if (-not (Test-Path $key)) { throw "缺少 SSH 私钥：$key（请先生成）" }

# 让 git 用 Windows 自带 OpenSSH + 本项目专用密钥，不污染全局 ~/.ssh
& $git -C $Root config core.sshCommand "`"$ssh`" -i `"$key`" -o StrictHostKeyChecking=accept-new"

& $git -C $Root remote remove origin 2>$null
& $git -C $Root remote add origin $RepoUrl

Write-Host "推送到 $RepoUrl ..." -ForegroundColor Cyan
& $git -C $Root push -u origin $Branch
if ($LASTEXITCODE -ne 0) { throw '推送失败，请检查仓库地址与 SSH 公钥是否已添加到 GitHub' }

Write-Host '推送完成。' -ForegroundColor Green
& $git -C $Root remote -v
