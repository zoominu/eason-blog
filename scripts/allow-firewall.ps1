# 允许虚拟机 / 局域网设备访问本地开发端口
# 需要「以管理员身份」运行：
#   powershell -ExecutionPolicy Bypass -File scripts\allow-firewall.ps1
#
# 只放行开发用端口；生产环境请用 Nginx 统一入口，不要把 8080 直接暴露到公网。

#Requires -RunAsAdministrator

$ErrorActionPreference = 'Stop'

$rules = @(
    @{ Name = 'EasonBlog-Dev-Frontend'; Port = 5173; Desc = 'Eason Blog 前台 Vite 开发服务器' },
    @{ Name = 'EasonBlog-Dev-Admin';    Port = 5174; Desc = 'Eason Blog 后台 Vite 开发服务器' },
    @{ Name = 'EasonBlog-Dev-Backend';  Port = 8080; Desc = 'Eason Blog 后端 Spring Boot' }
)

foreach ($rule in $rules) {
    # 先删后建，保证重复执行不会堆出一堆同名规则
    netsh advfirewall firewall delete rule name="$($rule.Name)" 2>$null | Out-Null
    netsh advfirewall firewall add rule `
        name="$($rule.Name)" `
        dir=in action=allow protocol=TCP `
        localport=$($rule.Port) `
        profile=any `
        description="$($rule.Desc)" | Out-Null
    Write-Host "已放行 TCP $($rule.Port)（$($rule.Name)）" -ForegroundColor Green
}

Write-Host ''
Write-Host '下一步：' -ForegroundColor Cyan
Write-Host '  1. 在宿主机执行 ipconfig，确认虚拟机所在网络的网关/主机地址；'
Write-Host '  2. 在虚拟机里访问 http://<宿主机IP>:5173（不要用 127.0.0.1）。'
