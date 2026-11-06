# Eason Blog V1.0 Linux 部署手册

> 适用系统：Debian 12 / Ubuntu 22.04+（x86_64）
> 技术栈：Java 21 + Spring Boot 3.3 + MySQL 8 + Nginx + Vue 3 静态文件
> 适用版本：`v1.0.0` 发布包（`eason-blog-v1.0.0-linux.tar.gz`）

---

## 1. 架构与端口

```
浏览器
  │
  ├── 80/443（可选：雷池 WAF / 云负载均衡）
  │        │
  │        ▼
  └── Nginx（本机，默认 80；在 WAF 后面时用 8080）
           ├── /            → /var/www/eason-blog/frontend（前台 SPA）
           ├── /admin/      → /var/www/eason-blog/admin（后台 SPA）
           ├── /api/        → 127.0.0.1:8081（Spring Boot）
           └── /uploads/    → /opt/eason-blog/data/uploads（静态资源）
                                  │
                                  ▼
                            MySQL 8（127.0.0.1:3306）
```

| 组件 | 默认地址 | 说明 |
| --- | --- | --- |
| Nginx | `0.0.0.0:80` | 只暴露 80/443；后端、数据库不对公网开放 |
| Spring Boot | `127.0.0.1:8081` | systemd 管理，`SERVER_PORT=8081` |
| MySQL | `127.0.0.1:3306` | 库 `eason_blog`，账号 `blog@127.0.0.1` |
| 上传目录 | `/opt/eason-blog/data/uploads` | 需要可写、需要备份 |

> 如果前面还有雷池/其它 WAF：Nginx 改为 `listen 8080`，WAF 上游填 `http://127.0.0.1:8080`；
> 同时把 WAF 的地址加入 `TRUSTED_PROXIES`，否则限流会把所有访客当成同一个 IP。

---

## 2. 服务器要求

| 项目 | 最低 | 推荐 |
| --- | --- | --- |
| CPU | 1 核 | 2 核 |
| 内存 | 1 GB（需 2 GB swap） | 2 GB+ |
| 磁盘 | 10 GB | 20 GB+（视频/图片多则更大） |
| 系统 | Debian 12 / Ubuntu 22.04 | 同上 |
| 软件 | Java 21、MySQL 8、Nginx 1.18+ | 最新稳定版 |

> 1 GB 内存机器请务必加 swap，并把 JVM 限制在 256~384 MB（见第 6 节）。

---

## 3. 安装依赖

### Debian / Ubuntu

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless mysql-server nginx curl unzip tar
java -version          # 应显示 21.x
mysql --version        # 应显示 8.x
nginx -v
```

如果发行版没有 `openjdk-21-jre-headless`，安装 Temurin 21：

```bash
sudo apt install -y wget apt-transport-https gpg
wget -qO - https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo gpg --dearmor -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo $VERSION_CODENAME) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt update && sudo apt install -y temurin-21-jre
```

---

## 4. 创建系统用户与目录

```bash
# 专用低权限用户，不能登录 shell
sudo useradd -r -m -d /opt/eason-blog -s /usr/sbin/nologin easonblog

sudo mkdir -p /opt/eason-blog/{app,data/uploads,logs}
sudo mkdir -p /var/www/eason-blog
sudo chown -R easonblog:easonblog /opt/eason-blog
sudo chown -R www-data:www-data /var/www/eason-blog
```

最终目录：

```
/opt/eason-blog/
├── app/app.jar
├── data/uploads/
└── logs/
/var/www/eason-blog/
├── frontend/
└── admin/
```

---

## 5. 初始化 MySQL

```bash
sudo mysql
```

```sql
CREATE DATABASE eason_blog
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

CREATE USER 'blog'@'127.0.0.1' IDENTIFIED BY '替换成强密码';
GRANT ALL PRIVILEGES ON eason_blog.* TO 'blog'@'127.0.0.1';
FLUSH PRIVILEGES;
EXIT;
```

说明：

- 应用通过 `DB_HOST=127.0.0.1` 连接，账号只允许本机，不要用 `%`。
- 数据库结构由 Flyway 在首次启动时自动创建/升级，**不要手工建表**。
- 建议设置时区：`sudo timedatectl set-timezone Asia/Shanghai`。

---

## 6. 部署发布包

把 `eason-blog-v1.0.0-linux.tar.gz` 上传到服务器（`scp` / `sftp`），然后：

```bash
cd /tmp
tar -xzf eason-blog-v1.0.0-linux.tar.gz
cd eason-blog-v1.0.0

# 后端
sudo cp app.jar /opt/eason-blog/app/app.jar

# 前台/后台静态文件
sudo rm -rf /var/www/eason-blog/frontend /var/www/eason-blog/admin
sudo cp -r frontend /var/www/eason-blog/frontend
sudo cp -r admin /var/www/eason-blog/admin

sudo chown -R easonblog:easonblog /opt/eason-blog
sudo chown -R www-data:www-data /var/www/eason-blog
```

> 发布包里还包含 `deploy/` 模板（systemd、env、Nginx）和 `docs/`，可按需复制。

---

## 7. 配置环境变量

```bash
sudo cp /tmp/eason-blog-v1.0.0/deploy/eason-blog.env.example /etc/eason-blog.env
sudo chmod 600 /etc/eason-blog.env
sudo nano /etc/eason-blog.env
```

最少需要修改：

```ini
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8081

DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=eason_blog
DB_USER=blog
DB_PASSWORD=第 5 步设置的密码

# 必填：至少 32 字节随机串，prod 缺失会直接启动失败
JWT_SECRET=把 openssl rand -base64 48 的输出粘进来
JWT_EXPIRE_MINUTES=1440

# HTTP 部署填 false；上 HTTPS 后改 true 并重启
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax

UPLOAD_DIR=/opt/eason-blog/data/uploads
SITE_URL=https://你的域名

# 允许的前端来源（同域部署可保留）
CORS_ALLOWED_ORIGINS=https://你的域名

# 前置代理地址/网段，逗号分隔；应用从右往左跳过这些地址取真实客户端 IP
TRUSTED_PROXIES=127.0.0.0/8,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,::1/128
```

生成随机密钥：

```bash
openssl rand -base64 48
```

首次初始化管理员（只在库中没有管理员时生效，创建成功后建议从 env 删除密码）：

```ini
BLOG_ADMIN_EMAIL=admin@example.com
BLOG_ADMIN_PASSWORD=替换成强密码
BLOG_ADMIN_NICKNAME=Eason
```

> `SITE_URL` 用于邮件里的链接；如果后台「系统设置 → 站点地址」已有值，以数据库配置为准。
> 管理员登录使用**账号名**（默认 `admin`）而不是邮箱。

---

## 8. 安装 systemd 服务

```bash
sudo cp /tmp/eason-blog-v1.0.0/deploy/eason-blog.service /etc/systemd/system/eason-blog.service
sudo systemctl daemon-reload
sudo systemctl enable --now eason-blog
sudo systemctl status eason-blog --no-pager
```

查看启动日志（首次启动会跑 Flyway，看到 `Successfully applied ... migration` 即正常）：

```bash
sudo journalctl -u eason-blog -n 100 --no-pager
```

健康检查：

```bash
curl -s http://127.0.0.1:8081/actuator/health
# {"status":"UP"}
```

> 1 GB 内存机器可把 service 里的 JVM 参数改为：
> `-Xms96m -Xmx256m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+UseSerialGC`
> 并确认已启用 swap。

---

## 9. 配置 Nginx

```bash
sudo cp /tmp/eason-blog-v1.0.0/deploy/nginx-eason-blog.conf /etc/nginx/sites-available/eason-blog.conf
sudo ln -sf /etc/nginx/sites-available/eason-blog.conf /etc/nginx/sites-enabled/eason-blog.conf
sudo rm -f /etc/nginx/sites-enabled/default
sudo nginx -t
sudo systemctl reload nginx
```

模板里的关键点：

```nginx
absolute_redirect off;              # /admin 跳转不要带 :8080
client_max_body_size 210m;          # 与后端 200MB 上传限制对齐

location / { try_files $uri $uri/ /index.html; }
location /admin/ { alias /var/www/eason-blog/admin/; try_files $uri $uri/ /admin/index.html; }
location /api/ { proxy_pass http://127.0.0.1:8081; ... }
location /uploads/ { alias /opt/eason-blog/data/uploads/; }
```

如果 Nginx 在 WAF 后面，把 `listen 80;` 改成 `listen 8080;`。

---

## 10. 防火墙与端口收敛

只对公网开放 80/443（或 WAF 需要的端口），**不要**开放 8081/3306：

```bash
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

检查监听：

```bash
ss -lntp | grep -E ':(80|443|8080|8081|3306)\s'
```

建议：

- Spring Boot 只监听 `127.0.0.1`：`SERVER_PORT=8081` 且 Nginx 反代本机；如使用 systemd 可加 `--server.address=127.0.0.1`。
- MySQL 在 `/etc/mysql/mysql.conf.d/mysqld.cnf` 设置 `bind-address = 127.0.0.1`，重启 MySQL。
- 不要长期把 8080 直接暴露公网；正式入口走 WAF/Nginx。

---

## 11. 验证清单

```bash
# 首页/后台静态
curl -I http://127.0.0.1/
curl -I http://127.0.0.1/admin/
# API
curl -s http://127.0.0.1/api/v1/articles?size=1
curl -s http://127.0.0.1/actuator/health
# 上传目录
curl -I http://127.0.0.1/uploads/
```

浏览器验证：

1. 打开 `http://服务器IP/`，首页、文章详情、分类/标签、搜索正常；
2. 打开 `/admin/`，用账号 `admin` + 初始化密码登录；
3. 后台发一篇草稿、上传封面、发布；
4. 注册一个普通用户（若开启邮件，需要 SMTP 配好并收验证码）；
5. 评论、点赞、收藏、留言、订阅开关正常；
6. 上传一张图片/一个视频，`/uploads/...` 能访问。

首次上线务必：

- 后台「系统设置 → 邮件设置」填 SMTP 授权码和站点地址；
- 修改管理员密码，并从 `/etc/eason-blog.env` 删除 `BLOG_ADMIN_PASSWORD`；
- 配置 HTTPS 后把 `COOKIE_SECURE=true`，重启后端。

---

## 12. HTTPS / WAF（可选）

1. 在 WAF 或 Nginx 配置 443 证书；
2. `COOKIE_SECURE=true`（`/etc/eason-blog.env`）；
3. `TRUSTED_PROXIES` 加入 WAF 的出口地址（如 `39.96.94.89`）；
4. `sudo systemctl restart eason-blog`；
5. 浏览器确认登录 Cookie 带 `Secure`，`http://` 自动跳 `https://`。

> 雷池接入时：站点上游填 `http://127.0.0.1:8080`，本机 Nginx 监听 8080；
> 雷池和 Nginx 不能同时监听 80。

---

## 13. 升级与回滚

### 升级

```bash
# 1. 备份（强烈建议）
sudo /usr/local/bin/eason-blog-backup.sh

# 2. 替换 jar 和静态文件（保留 data/uploads）
sudo systemctl stop eason-blog
sudo cp app.jar /opt/eason-blog/app/app.jar
sudo rm -rf /var/www/eason-blog/frontend /var/www/eason-blog/admin
sudo cp -r frontend /var/www/eason-blog/frontend
sudo cp -r admin /var/www/eason-blog/admin
sudo chown -R easonblog:easonblog /opt/eason-blog
sudo chown -R www-data:www-data /var/www/eason-blog
sudo systemctl start eason-blog

# 3. 看 Flyway 与健康检查
sudo journalctl -u eason-blog -n 80 --no-pager
curl -s http://127.0.0.1:8081/actuator/health
```

Flyway 会按 `V1...V12` 自动升级；**不要跳过版本或手工改表**。

### 回滚

```bash
sudo systemctl stop eason-blog
sudo cp /opt/eason-blog/app/app.jar.bak /opt/eason-blog/app/app.jar
# 静态文件回滚到上一版目录
sudo systemctl start eason-blog
```

如果数据库迁移导致不兼容，需从备份恢复数据库（见下一节）。

---

## 14. 备份与恢复

### 备份脚本示例

`/usr/local/bin/eason-blog-backup.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail
STAMP=$(date +%Y%m%d-%H%M%S)
DIR=/var/backups/eason-blog
mkdir -p "$DIR"

# 数据库（--no-tablespaces 避免 PROCESS 权限报错）
mysqldump -uroot --no-tablespaces --single-transaction \
  --routines --triggers eason_blog | gzip > "$DIR/db-$STAMP.sql.gz"

# 上传文件
tar -czf "$DIR/uploads-$STAMP.tar.gz" -C /opt/eason-blog/data uploads

# 只保留 14 天
find "$DIR" -type f -mtime +14 -delete
```

```bash
sudo chmod +x /usr/local/bin/eason-blog-backup.sh
# 每天 03:20
echo '20 3 * * * root /usr/local/bin/eason-blog-backup.sh' | sudo tee /etc/cron.d/eason-blog-backup
```

### 恢复

```bash
sudo systemctl stop eason-blog

gunzip < /var/backups/eason-blog/db-XXXX.sql.gz | sudo mysql eason_blog

sudo rm -rf /opt/eason-blog/data/uploads/*
sudo tar -xzf /var/backups/eason-blog/uploads-XXXX.tar.gz -C /opt/eason-blog/data
sudo chown -R easonblog:easonblog /opt/eason-blog/data

sudo systemctl start eason-blog
```

---

## 15. 日志与排障

```bash
sudo journalctl -u eason-blog -f              # 后端
sudo tail -f /var/log/nginx/error.log         # Nginx
sudo tail -f /var/log/nginx/access.log
sudo systemctl status eason-blog nginx mysql
```

| 现象 | 排查 |
| --- | --- |
| 502 Bad Gateway | 后端没起来：`systemctl status eason-blog`、`journalctl -u eason-blog -n 100` |
| 启动报 `JWT_SECRET` | `/etc/eason-blog.env` 没配或少于 32 字节；prod 缺失会 fail-fast |
| 登录后立刻 401 | 检查 `COOKIE_SECURE`（HTTPS 必须 true，HTTP 必须 false）、系统时间、Nginx 是否改写 Cookie |
| 上传 413 | Nginx `client_max_body_size` 小于文件；模板是 210m，后端限制 200MB |
| 上传 400 类型不支持 | SVG 已禁用；只接受图片/音频/视频/PDF/ZIP/纯文本/Markdown |
| `/admin` 跳转到 `:8080` | Nginx 缺 `absolute_redirect off;` |
| WAF 返回 468 | 请求被雷池拦截；检查 UA/频率/自定义头，确认站点上游和可信代理配置 |
| 邮件发不出 | 后台「系统设置 → 邮件设置」检查 SMTP、授权码、`site.url`；看后端日志 `SMTP 发送失败` |
| 数据库连不上 | `mysql -h127.0.0.1 -ublog -p`；确认 `bind-address=127.0.0.1`、账号 host 是 `127.0.0.1` |
| 内存不足/OOM | 加 swap、降 `-Xmx`、限制 MySQL buffer pool |

---

## 16. 发布包目录

```
eason-blog-v1.0.0/
├── app.jar                       # Spring Boot 可执行 jar
├── frontend/                     # 前台 dist
├── admin/                        # 后台 dist（/admin/ 子路径）
├── deploy/
│   ├── eason-blog.service        # systemd 模板
│   ├── eason-blog.env.example    # 环境变量模板
│   └── nginx-eason-blog.conf     # Nginx 模板
├── docs/
│   ├── LINUX_DEPLOY.md           # 本文
│   ├── API.md
│   ├── DATABASE.md
│   ├── MAINTENANCE.md
│   └── DEVELOPMENT_LOG.md
├── VERSION
└── SHA256SUMS
```

校验发布包：

```bash
sha256sum -c SHA256SUMS
```

---

## 17. 安全基线（上线前逐项确认）

- [ ] `JWT_SECRET` 是随机值，未使用仓库默认值，权限 600；
- [ ] `SPRING_PROFILES_ACTIVE=prod`，无演示数据；
- [ ] MySQL 只监听 127.0.0.1，账号不用 `%`；
- [ ] 公网只开 80/443（或 WAF 端口），8081/3306 不可达；
- [ ] `TRUSTED_PROXIES` 只包含真实代理地址；
- [ ] HTTPS 后 `COOKIE_SECURE=true`；
- [ ] 管理员密码已修改，初始密码从 env 删除；
- [ ] 备份 cron 正常运行，至少做过一次恢复演练；
- [ ] 上传目录不在 Nginx 可执行路径内（模板只做静态返回）；
- [ ] 定期 `apt upgrade`，关注 Java/MySQL/Nginx 安全更新。
