# Eason Blog 维护手册（虚拟机部署版）

> 适用环境：Ubuntu 24.04 虚拟机，`192.168.245.131`，通过 Nginx 反向代理对外提供服务。
> 本文档记录**实际部署结果**：目录、端口、账号、命令、备份与排错，均按当前机器为准。

---

## 1. 访问信息

| 项 | 值 |
| --- | --- |
| 前台 | http://192.168.245.131/ |
| 后台 | http://192.168.245.131/admin/ |
| 管理员账号 | `admin@eason.blog` / `Admin123456`（**首次登录后请改密码**） |
| SSH | `ssh eason@192.168.245.131`（密码 `eas0n.top`） |
| 服务器时区 | Asia/Shanghai |
| 运行用户 | 应用 `eason`；静态站点 `www-data` |

> 生产库默认只有分类/标签/专栏等基础数据；演示文章与测试用户可按第 6.4 节导入。
> 上线第一件事：登录后台 → 文章 → 新建。

### 6.4 演示数据与测试账号（已导入）

当前虚拟机已导入：

| 数据 | 内容 |
| --- | --- |
| 演示文章 | 5 篇（Java 21 / Spring Boot 骨架 / MySQL 索引 / Vue 前端 / 工程实践） |
| 分类 / 标签 | 5 个分类、8 个标签 |
| 测试用户 | `reader1@eason.blog`、`reader2@eason.blog`，密码均为 `Test123456` |

重新导入或给新环境导入演示文章：

```bash
# 脚本在仓库 deploy/vm/seed-demo.sh
scp deploy/vm/seed-demo.sh eason@192.168.245.131:/tmp/
ssh eason@192.168.245.131 "sudo bash /tmp/seed-demo.sh"
```

脚本原理：临时把 `APP_DEMODATA_ENABLED=true` 写入 `/etc/eason-blog.env` 并重启，让
`DemoDataSeeder` 幂等插入演示文章（按 slug 判重），完成后自动改回 `false` 再重启。

批量补测试用户 / 示例资源（需要管理员密码）：

```powershell
cd eason-blog
node scripts/seed-vm-demo.mjs http://192.168.245.131
```

---

## 2. 组成与端口

| 组件 | 版本 | 端口 | 进程 / 服务名 |
| --- | --- | --- | --- |
| JDK | OpenJDK 21.0.12 | — | 系统包 |
| MySQL | 8.0.46 | 3306（仅 127.0.0.1） | `mysql.service` |
| 后端 Spring Boot | 1.0.0 | 8080（仅本机） | `eason-blog.service` |
| Nginx | 1.24.0 | 80（对外） | `nginx.service` |
| 前台 / 后台 | Vue 3 静态文件 | 80 | 由 Nginx 直接返回 |

请求路径：

```
/            → /var/www/eason-blog/index.html（SPA，history 回退）
/admin/      → /var/www/eason-blog/admin/index.html（SPA，base=/admin/）
/api/        → 反代 127.0.0.1:8080
/uploads/    → /opt/eason-blog/data/uploads/
/rss.xml /sitemap.xml /robots.txt → 反代后端
/actuator/   → 404（不对外）
```

---

## 3. 文件存放位置

### 3.1 应用与数据

| 路径 | 内容 | 属主 |
| --- | --- | --- |
| `/opt/eason-blog/app/blog-server.jar` | 后端可执行 jar | eason |
| `/opt/eason-blog/data/uploads/` | 用户上传文件（按 `yyyy/MM` 分目录，含 Markdown 导入的图片与 `avatar/` 头像） | eason |
| `/var/www/eason-blog/` | 前台构建产物（index.html、assets/） | www-data |
| `/var/www/eason-blog/admin/` | 后台构建产物 | www-data |
| `/etc/eason-blog.env` | 全部运行参数与密钥（**600**） | root |
| `/etc/systemd/system/eason-blog.service` | 后端服务定义 | root |
| `/etc/nginx/sites-available/eason-blog.conf` | Nginx 站点配置 | root |
| `/etc/nginx/sites-enabled/eason-blog.conf` | 软链接 | root |
| `/var/log/nginx/access.log`、`error.log` | 访问 / 错误日志 | root |

### 3.2 数据库

| 项 | 值 |
| --- | --- |
| 库名 | `eason_blog`（utf8mb4 / utf8mb4_0900_ai_ci） |
| 应用账号 | `blog@127.0.0.1` |
| 密码 | 见 `/etc/eason-blog.env` 的 `DB_PASSWORD` |
| 表结构 | 由 Flyway 启动时自动迁移（`V1`~`V5`） |
| 数据目录 | `/var/lib/mysql` |

> **不要手工改表结构**：改结构要新增 `V6__xxx.sql` 迁移文件并重新发布，否则 Flyway 校验会失败。

### 3.3 本地仓库里对应的部署资产

| 仓库路径 | 用途 |
| --- | --- |
| `deploy/vm/install.sh` | 远端一键安装 / 升级（幂等） |
| `deploy/vm/eason-blog.service` | systemd 单元模板 |
| `deploy/vm/eason-blog.conf` | Nginx 站点模板 |
| `scripts/deploy-vm.ps1` | Windows 侧一键构建 + 上传 + 安装 |
| `scripts/verify-vm.mjs` | 部署后验收（17 项） |

---

## 4. 日常运维命令

```bash
# 服务状态
sudo systemctl status eason-blog
sudo systemctl status mysql nginx

# 启停 / 重启
sudo systemctl restart eason-blog        # 改配置或换 jar 后
sudo systemctl stop eason-blog
sudo systemctl start eason-blog

# 实时日志
sudo journalctl -u eason-blog -f
sudo journalctl -u eason-blog -n 200 --no-pager

# Nginx
sudo nginx -t && sudo systemctl reload nginx
sudo tail -f /var/log/nginx/error.log

# 端口检查
ss -lntp | grep -E ':(80|8080|3306)\b'

# 健康检查（只能在服务器本机执行，Nginx 不对外暴露）
curl -s http://127.0.0.1:8080/actuator/health
```

---

## 5. 发布 / 升级

### 5.1 推荐：Windows 侧一条命令

```powershell
cd eason-blog
$env:VM_PASSWORD = 'eas0n.top'
pwsh -File scripts/deploy-vm.ps1
```

脚本会：构建 jar 与前台/后台（后台自动加 `--base=/admin/`）→ 打包 → scp → 远端执行
`install.sh` → 跑 `verify-vm.mjs` 验收。

只改了配置、想快速重发：

```powershell
pwsh -File scripts/deploy-vm.ps1 -SkipBuild
```

### 5.2 手工发布（服务器上）

```bash
# 1) 本地传包
scp eason-blog/.tools/vm-bundle.tar.gz eason@192.168.245.131:/tmp/
# 2) 服务器
ssh eason@192.168.245.131
rm -rf /tmp/eason-blog-bundle && mkdir -p /tmp/eason-blog-bundle
tar -xzf /tmp/eason-blog-bundle.tar.gz -C /tmp/eason-blog-bundle
sudo bash /tmp/eason-blog-bundle/install.sh
```

`install.sh` 是幂等的：代码与 Nginx 配置会被覆盖，**`/etc/eason-blog.env` 与数据库不会被重置**。

### 5.3 只换后端 jar

```bash
sudo cp /tmp/blog-server.jar /opt/eason-blog/app/blog-server.jar
sudo chown eason:eason /opt/eason-blog/app/blog-server.jar
sudo systemctl restart eason-blog
sudo journalctl -u eason-blog -n 50 --no-pager
```

### 5.4 回滚

保留上一版 jar 与 dist 即可回滚：

```bash
sudo cp /opt/eason-blog/app/blog-server.jar.bak /opt/eason-blog/app/blog-server.jar
sudo systemctl restart eason-blog
```

> 数据库迁移只向前：如果新版本包含破坏性迁移，回滚 jar 前先用备份恢复数据库。

---

## 6. 备份与恢复

### 6.1 备份（数据库 + 上传）

```bash
# 一次性备份到 /var/backups/eason-blog
sudo mkdir -p /var/backups/eason-blog
STAMP=$(date +%Y%m%d-%H%M%S)
sudo mysqldump --single-transaction --routines --triggers \
  --default-character-set=utf8mb4 eason_blog > /tmp/eason_blog-$STAMP.sql
sudo mv /tmp/eason_blog-$STAMP.sql /var/backups/eason-blog/
sudo tar -czf /var/backups/eason-blog/uploads-$STAMP.tar.gz -C /opt/eason-blog/data uploads
sudo find /var/backups/eason-blog -type f -mtime +14 -delete
```

### 6.2 定时任务（每天 3:10）

```bash
sudo crontab -e
# 加入：
10 3 * * * mysqldump --single-transaction --default-character-set=utf8mb4 eason_blog | gzip > /var/backups/eason-blog/eason_blog-$(date +\%F).sql.gz
20 3 * * * tar -czf /var/backups/eason-blog/uploads-$(date +\%F).tar.gz -C /opt/eason-blog/data uploads
```

### 6.3 恢复

```bash
sudo systemctl stop eason-blog
# 恢复数据库（会覆盖现有数据）
sudo mysql -e "DROP DATABASE IF EXISTS eason_blog; CREATE DATABASE eason_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
sudo mysql eason_blog < /var/backups/eason-blog/eason_blog-20261104-031000.sql
# 恢复上传
sudo rm -rf /opt/eason-blog/data/uploads/*
sudo tar -xzf /var/backups/eason-blog/uploads-20261104-031000.tar.gz -C /opt/eason-blog/data
sudo chown -R eason:eason /opt/eason-blog/data
sudo systemctl start eason-blog
```

> 恢复后 `blog` 账号权限仍在；如果库被重建，执行一次授权：
> `sudo mysql -e "GRANT ALL ON eason_blog.* TO 'blog'@'127.0.0.1'; FLUSH PRIVILEGES;"`

---

## 7. 配置项（`/etc/eason-blog.env`）

修改后必须 `sudo systemctl restart eason-blog`。

| 变量 | 说明 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 固定 `prod`（不加载演示数据） |
| `DB_HOST/DB_PORT/DB_NAME/DB_USER/DB_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | 签发 Cookie 的密钥，≥32 字节；**泄露等于可伪造登录** |
| `JWT_EXPIRE_MINUTES` | 登录有效期，默认 120 分钟 |
| `COOKIE_SECURE` | HTTP 部署必须 `false`；上 HTTPS 后改 `true` |
| `COOKIE_SAME_SITE` | 默认 `Lax` |
| `UPLOAD_DIR` | 上传目录，默认 `/opt/eason-blog/data/uploads` |
| `CORS_ALLOWED_ORIGINS` | 同域部署时可不改 |
| `BLOG_ADMIN_EMAIL/PASSWORD` | 仅首次启动创建管理员；创建后建议删除这两行 |
| `SERVER_PORT` | 后端端口，默认 8080 |

查看当前值（注意含密钥，勿外传）：

```bash
sudo cat /etc/eason-blog.env
```

### 7.1 评论 / 留言审核策略

在后台「系统设置 → 审核与违禁词」调整（即时生效，无需重启）：

| 配置 | 作用 |
| --- | --- |
| `comment.audit` / `guestbook.audit` = 需要审核后展示 | 所有评论/留言先待审核（旧行为） |
| 上面两项 = 直接展示 | 默认直接发布，只有命中违禁词才处理 |
| `audit.banned-words` | 违禁词，每行一个或逗号分隔 |
| `audit.banned-action` = 转人工审核 | 命中后进入待审核列表 |
| `audit.banned-action` = 替换为 * 后展示 | 命中词替换成等长 `*`，直接发布 |

> 管理员（SUPER_ADMIN）发布的评论/回复**始终免审**，直接展示。
> 当前虚拟机配置：`comment.audit=false`、`guestbook.audit=false`，违禁词为空、命中转审核。

修改可用后台界面，也可调接口：

```bash
curl -X PATCH http://127.0.0.1/api/v1/admin/settings \
  -H 'Content-Type: application/json' -b 'blog_token=<管理员 Cookie>' \
  -d '{"comment.audit":"false","audit.banned-words":"违禁词1\n违禁词2","audit.banned-action":"review"}'
```

### 7.2 邮件（注册验证码 / 订阅通知）

后台「系统设置 → 邮件设置」填写（即时生效）：

| 配置 | 说明 |
| --- | --- |
| `mail.enabled` | 开启后注册必须邮箱验证码，发布文章会给订阅者发邮件 |
| `mail.host` / `mail.port` | SMTP 地址；QQ 邮箱 `smtp.qq.com:465` |
| `mail.username` | 完整邮箱地址 |
| `mail.password` | **SMTP 授权码**（QQ/163 在邮箱设置里生成，不是登录密码） |
| `mail.from` | 发件人显示地址，留空用 username |
| `mail.ssl` | 465 端口选「是」；587 选「否」（STARTTLS） |
| `site.url` | 站点对外地址，用于邮件里的文章链接，如 `http://39.96.94.89` |

填写后点「保存修改」，再点「发送测试邮件」（收件人默认用 SMTP 账号）。
发送失败会直接显示 SMTP 返回的错误（授权码错、端口不对等）。

> 注意：开启 `mail.enabled` 后，新注册用户必须先点「发送验证码」并填入 6 位验证码。

---

## 8. 日志与排查

### 8.1 日志位置

| 日志 | 位置 | 用途 |
| --- | --- | --- |
| 后端应用 | `journalctl -u eason-blog` | 启动、异常、Flyway、慢请求 |
| Nginx 访问 | `/var/log/nginx/access.log` | 谁访问了什么、状态码 |
| Nginx 错误 | `/var/log/nginx/error.log` | 502/403/404 的直接原因 |
| MySQL | `/var/log/mysql/error.log` | 连接失败、崩溃 |
| 登录/操作审计 | 后台「日志」页 / `blog_login_log`、`blog_operation_log` | 谁登录、谁改了什么 |

### 8.2 常见问题

| 现象 | 排查 | 处理 |
| --- | --- | --- |
| 打开站点 403 | `ls -l /var/www/eason-blog`；看 error.log 是否 "directory index forbidden" | 前台文件必须在站点根（不是 `frontend/` 子目录）；`sudo chown -R www-data:www-data /var/www/eason-blog` |
| 502 Bad Gateway | `systemctl status eason-blog`；`journalctl -u eason-blog -n 100` | 后端没起来，多为数据库连不上或 8080 被占 |
| 登录后立刻掉线 | 浏览器是否带 Cookie；`COOKIE_SECURE` 是否为 `false` | HTTP 环境必须关掉 Secure；改完重启 |
| 后台页面白屏 | F12 看 `/admin/assets/*.js` 是否 404 | 后台必须用 `ADMIN_BASE=/admin/` 构建 |
| 上传失败 400/413 | 文件 > 5MB 或扩展名不在白名单 | 合规格式：png/jpg/jpeg/gif/webp/svg/pdf/txt/md/zip |
| Flyway 校验失败 | 是否手工改过库结构/迁移文件 | 用备份恢复，或新增迁移而不是修改历史迁移 |
| 磁盘满 | `df -h` | 清理 `/var/backups`、`journalctl --vacuum-time=7d` |
| 内存不足 | `free -h`、`dmesg | grep -i oom` | 调小 `-Xmx`（systemd 里的 512m） |

### 8.3 快速自检脚本

```bash
curl -s -o /dev/null -w "home:%{http_code}\n" http://127.0.0.1/
curl -s -o /dev/null -w "admin:%{http_code}\n" http://127.0.0.1/admin/
curl -s -o /dev/null -w "api:%{http_code}\n" http://127.0.0.1/api/v1/articles
curl -s http://127.0.0.1:8080/actuator/health; echo
```

Windows 侧完整验收（17 项，含上传/删除、登录、RSS）：

```powershell
cd eason-blog
node scripts/verify-vm.mjs http://192.168.245.131
```

---

## 9. 安全建议（按优先级）

1. **改管理员密码**：登录前台 `/user` 修改，或在后台用户管理里新建管理员后删除默认账号。
2. **改 MySQL 密码**：改 `/etc/eason-blog.env` → `ALTER USER 'blog'@'127.0.0.1' ...` → 重启后端。
3. **限制 SSH**：改用密钥登录并禁用密码：
   ```bash
   ssh-keygen -t ed25519            # 本地生成
   ssh-copy-id eason@192.168.245.131
   sudo sed -i 's/^#\?PasswordAuthentication.*/PasswordAuthentication no/' /etc/ssh/sshd_config
   sudo systemctl restart ssh
   ```
4. **防火墙**：`sudo ufw allow 22,80/tcp && sudo ufw enable`（仅放行必要端口；8080/3306 不对外开放）。
5. **上 HTTPS**：有域名后配置证书，同时把 `COOKIE_SECURE=true`、`CORS_ALLOWED_ORIGINS` 改成域名。
6. **定期升级**：`sudo apt update && sudo apt upgrade`，升级后重启服务并跑一次验收脚本。
7. **备份加密/异地**：`/var/backups` 只是本机，重要数据应再同步到别的机器或网盘。

---

## 10. 变更记录

| 日期 | 内容 |
| --- | --- |
| 2026-11-04 | 首次部署：Ubuntu 24.04 + JDK 21 + MySQL 8.0.46 + Nginx 1.24；站点 / 后台 / API / 上传 / RSS 全部验收通过 |
