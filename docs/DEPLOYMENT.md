# 部署说明（Linux VPS / NAS）

目标形态：Nginx 提供 HTTPS 与静态资源，`/api/` 反向代理到 Spring Boot，MySQL 仅本机监听。

```
浏览器 ──HTTPS──> Nginx ──/api/──> blog-server:8080 ──> MySQL:3306 (127.0.0.1)
                    └── 静态资源：/var/www/eason-blog/{frontend,admin}
```

---

## 1. 服务器准备

```bash
# Ubuntu 22.04 / 24.04 示例
sudo apt update
sudo apt install -y openjdk-21-jre-headless mysql-server nginx certbot python3-certbot-nginx

sudo mysql_secure_installation
```

创建数据库与账号（**不要用 root 跑应用**）：

```sql
CREATE DATABASE eason_blog CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'blog'@'127.0.0.1' IDENTIFIED BY '强随机密码';
GRANT ALL PRIVILEGES ON eason_blog.* TO 'blog'@'127.0.0.1';
FLUSH PRIVILEGES;
```

确认 MySQL 只监听本机：`/etc/mysql/mysql.conf.d/mysqld.cnf` 中 `bind-address = 127.0.0.1`。

## 2. 构建产物

本地或 CI 构建（推荐，服务器不装 Maven/Node）：

```bash
# 后端
cd blog-server && mvn -B -DskipTests package      # target/blog-server-0.1.0-SNAPSHOT.jar

# 前台（根路径部署）
cd blog-frontend && pnpm install && pnpm build    # dist/

# 后台（/admin/ 子路径部署：需要 base）
cd blog-admin && pnpm install && pnpm build --base=/admin/   # dist/
```

> 后台若独立域名部署（`admin.eas0n.top`），则 `base` 用默认 `/`，并把 Nginx 的 `/admin/` 换成独立 server 块。

上传：

```bash
sudo mkdir -p /var/www/eason-blog/{frontend,admin,uploads}
sudo rsync -a blog-frontend/dist/ /var/www/eason-blog/frontend/
sudo rsync -a blog-admin/dist/    /var/www/eason-blog/admin/
sudo mkdir -p /opt/eason-blog && sudo cp blog-server/target/blog-server-*.jar /opt/eason-blog/app.jar
sudo useradd -r -s /usr/sbin/nologin blog
sudo chown -R blog:blog /opt/eason-blog /var/www/eason-blog/uploads
```

## 3. 环境变量

放在 `/etc/eason-blog.env`（`chmod 600`，属主 root）：

```ini
SPRING_PROFILES_ACTIVE=prod
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=eason_blog
DB_USER=blog
DB_PASSWORD=<强随机密码>
JWT_SECRET=<openssl rand -base64 48>
JWT_EXPIRE_MINUTES=120
COOKIE_SECURE=true
COOKIE_SAME_SITE=Lax
CORS_ALLOWED_ORIGINS=https://www.eas0n.top
UPLOAD_DIR=/var/www/eason-blog/uploads

# 邮件（找回密码 / 邮箱验证）：不配则链接只写日志
MAIL_ENABLED=true
MAIL_FROM=Eason Blog <noreply@eas0n.top>
SITE_URL=https://www.eas0n.top
SPRING_MAIL_HOST=smtp.example.com
SPRING_MAIL_PORT=465
SPRING_MAIL_USERNAME=noreply@eas0n.top
SPRING_MAIL_PASSWORD=<SMTP 授权码>
SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true
SPRING_MAIL_PROPERTIES_MAIL_SMTP_SSL_ENABLE=true

# 仅首次启动需要；创建成功后删除这两行
BLOG_ADMIN_EMAIL=you@example.com
BLOG_ADMIN_PASSWORD=<一次性强密码>
```

> 邮件未配置时，`forgot-password` / 注册验证链接会以 `[mail disabled]` 前缀打印到后端日志，
> 开发环境可直接从日志复制链接；生产必须配置 SMTP，否则用户收不到重置邮件。

## 4. systemd

`/etc/systemd/system/eason-blog.service`：

```ini
[Unit]
Description=Eason Blog Server
After=network.target mysql.service

[Service]
Type=simple
User=blog
WorkingDirectory=/opt/eason-blog
EnvironmentFile=/etc/eason-blog.env
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=70 -Duser.timezone=Asia/Shanghai -jar /opt/eason-blog/app.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

# 基础加固
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/www/eason-blog/uploads

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now eason-blog
sudo journalctl -u eason-blog -f
curl -s http://127.0.0.1:8080/actuator/health
```

## 5. Nginx 与 HTTPS

```bash
sudo cp deploy/nginx/eason-blog.conf /etc/nginx/conf.d/eason-blog.conf
# 修改 server_name 与证书路径
sudo nginx -t && sudo systemctl reload nginx
sudo certbot --nginx -d www.eas0n.top -d eas0n.top
```

首次签发后把 80 端口的 `return 301` 保留，certbot 会自动续期（`systemctl list-timers | grep certbot`）。

## 6. 备份

```bash
# /etc/cron.d/eason-blog-backup
0 3 * * * blog mysqldump -u blog -p"$DB_PASSWORD" --single-transaction eason_blog | gzip > /backup/eason_blog_$(date +\%F).sql.gz
0 4 * * * root find /backup -name 'eason_blog_*.sql.gz' -mtime +14 -delete
```

同时备份 `/var/www/eason-blog/uploads`。**每季度做一次恢复演练**（在测试库导入并启动一次）。

## 7. 升级与回滚

```bash
# 升级：保留上一版 jar
sudo cp /opt/eason-blog/app.jar /opt/eason-blog/app.jar.bak
sudo rsync -a new-dist/frontend/ /var/www/eason-blog/frontend/
sudo cp new.jar /opt/eason-blog/app.jar
sudo systemctl restart eason-blog

# 回滚
sudo cp /opt/eason-blog/app.jar.bak /opt/eason-blog/app.jar
sudo systemctl restart eason-blog
```

数据库迁移只能向前：回滚代码前先确认新迁移向后兼容（只加列/加表，不删列），否则需从备份恢复。

## 8. 上线检查清单

- [ ] `COOKIE_SECURE=true`、HTTPS 正常、HTTP 全量跳转
- [ ] `JWT_SECRET` 为随机值且 ≥32 字节，未提交进仓库
- [ ] `BLOG_ADMIN_*` 已从环境文件移除（管理员已创建）
- [ ] MySQL 仅监听 127.0.0.1，应用账号无全局权限
- [ ] `/actuator/**` 仅内网可访问
- [ ] 上传目录不可执行脚本、大小限制生效
- [ ] 数据库与上传目录有每日备份，且验证过恢复
- [ ] 日志中无密码、token、Cookie 明文

## 9. Docker（可选）

```bash
cd deploy/docker
cp ../../.env.example .env   # 填入 MYSQL_ROOT_PASSWORD / DB_PASSWORD / JWT_SECRET
docker compose up -d --build
```

只容器化 MySQL 与后端；前端静态资源仍建议由宿主机 Nginx 提供，便于独立发布与 CDN 缓存。
