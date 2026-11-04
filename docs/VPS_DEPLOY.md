# VPS 部署记录（39.96.94.89，雷池源站）

> 部署时间：2026-11-04 · 系统：Debian 12 · 2 vCPU / 1.6GB 内存
> 机器上原有雷池 WAF 9.4.3（Docker，管理端 9443），博客以**源站端口 8080** 运行，
> 由雷池监听 80/443 反代出去，避免和雷池抢 80。

## 1. 架构与端口

```
访客 → 39.96.94.89:80/443（雷池 tengine，host 网络）
      → 127.0.0.1:8080（本机 Nginx：静态站 + /api 反代）
      → 127.0.0.1:8081（Spring Boot jar）
      → 127.0.0.1:3306（MySQL 8.0，仅本机）
```

| 组件 | 位置 / 端口 | 说明 |
| --- | --- | --- |
| Nginx | `127.0.0.1:8080` | 只监听回环，不占 80，雷池可直接反代 |
| 后端 | `127.0.0.1:8081` | systemd `eason-blog.service` |
| MySQL | `127.0.0.1:3306` | 库 `eason_blog`，用户 `blog@127.0.0.1` |
| 雷池 | `9443`（管理）+ 80/443（站点） | Docker，tengine 为 host 网络，可访问 127.0.0.1:8080 |

> 8080 已对公网放行（Nginx `listen 8080`），可直接访问 `http://39.96.94.89:8080/`。
> 走 8080 会绕过雷池 WAF，正式对外建议用雷池 80/443；确认雷池反代正常后，可在云安全组把 8080 收回。

## 2. 关键路径

| 路径 | 内容 |
| --- | --- |
| `/opt/eason-blog/app/app.jar` | 后端 jar |
| `/opt/eason-blog/data/uploads/` | 上传文件（图片/头像/导入 md 的图片） |
| `/opt/eason-blog/logs/` | 预留日志目录 |
| `/var/www/eason-blog/` | 前台静态文件；`admin/` 是后台 |
| `/etc/eason-blog.env` | 环境变量（600，含 DB 密码、JWT 密钥、管理员初始账号） |
| `/etc/systemd/system/eason-blog.service` | 后端服务 |
| `/etc/nginx/sites-available/eason-blog.conf` | 站点配置（8080） |
| `/var/backups/eason-blog/` | 每日备份（库 + uploads，保留 14 天） |

普通用户：`easonblog`（系统用户），拥有 `/opt/eason-blog`；`/var/www` 归 `www-data`。

## 3. 管理员与初始数据

- 地址：`http://39.96.94.89/admin/`（雷池配置 80 后）
- 账号：`admin@eason.blog` / `Admin123456`
- **上线后请立即在「个人中心 → 安全」改密码**，并同步修改 `/etc/eason-blog.env` 里的 `BLOG_ADMIN_PASSWORD`（仅用于首次初始化，改完可留空）。

数据库是全新的空库，只有 Flyway 初始化的表 + 角色权限 + 系统设置；文章/分类等需要自己添加。

## 4. 雷池接入步骤

1. 打开 `https://39.96.94.89:9443` 登录雷池管理端；
2. **站点 → 添加站点**：
   - 域名：填你的域名（没有域名可先用 `39.96.94.89`）
   - 上游服务器：`http://127.0.0.1:8080`
   - 端口：80（需要 HTTPS 再加 443 并上传证书）
3. 保存并「应用」配置；
4. 用浏览器访问 `http://<域名或IP>/`，应能看到博客首页；`/admin/` 是后台。

> 注意：雷池和本机 Nginx 不能同时监听 80。本机 Nginx 已固定 `127.0.0.1:8080`，不会冲突。
> 雷池的 tengine 是 host 网络，`127.0.0.1:8080` 可直接访问（已在本机用 curl 验证 200）。

如果之后启用 HTTPS：

1. 雷池站点加 443 + 证书；
2. 把 `/etc/eason-blog.env` 里 `COOKIE_SECURE=false` 改成 `true`；
3. `systemctl restart eason-blog`（否则浏览器不会在 HTTPS 下发送登录 Cookie）。

## 5. 内存与稳定性

机器只有 1.6GB，雷池已占 ~1.2GB，因此做了这些限制：

- 新增 2GB swap（`/swapfile`，已写 `/etc/fstab`），`vm.swappiness=10`；
- JVM：`-Xmx256m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC`；
- MySQL：`innodb_buffer_pool_size=96M`、`performance_schema=OFF`、`max_connections=50`、`mysqlx=0`
  （`/etc/mysql/mysql.conf.d/zz-eason-blog.cnf`）。

部署后实测：内存 used ~1.4G、swap 用 ~190M，无 OOM。若后续加内容/流量增加，建议升到 2GB 内存。

## 6. 日常运维

```bash
systemctl status eason-blog nginx mysql
journalctl -u eason-blog -n 100 --no-pager
tail -f /var/log/nginx/error.log
```

重启/重载：

```bash
systemctl restart eason-blog      # 后端
systemctl reload nginx            # 改 Nginx 配置后
```

备份（已加 cron，每天 03:20）：

```bash
/usr/local/bin/eason-blog-backup.sh
ls -lh /var/backups/eason-blog
```

恢复：

```bash
systemctl stop eason-blog
gunzip < /var/backups/eason-blog/db-XXXX.sql.gz | mysql -uroot eason_blog
rm -rf /opt/eason-blog/data/uploads/*
tar -xzf /var/backups/eason-blog/uploads-XXXX.tar.gz -C /opt/eason-blog/data
chown -R easonblog:easonblog /opt/eason-blog/data
systemctl start eason-blog
```

## 7. 更新发布

本机（Windows 开发机）构建后执行：

```powershell
$env:VPS_PASSWORD='aliyun.eas0n.top'
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/deploy-vps.ps1 -SkipBuild
```

脚本会打包 `app.jar + 前台 dist + 后台 dist`，上传到 VPS，替换文件并重启后端（Nginx 静态文件即时生效）。

> 只更新后端时也可手动：scp jar 到 `/opt/eason-blog/app/app.jar`，`systemctl restart eason-blog`。

## 8. 已验证

- 外网 `http://39.96.94.89:8080/` → 200（公网直连，绕过雷池）
- `curl http://127.0.0.1:8080/` → 200
- `curl http://127.0.0.1:8080/api/v1/articles?size=5` → 200
- `curl http://127.0.0.1:8080/admin/` → 200，静态资源 200
- 管理员登录 API → 200（`admin@eason.blog`）
- 资源上传 → 200，`/uploads/2026/11/...` 静态访问 200
- Flyway 7 个迁移全部成功
