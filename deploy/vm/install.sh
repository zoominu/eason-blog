#!/usr/bin/env bash
# ============================================================
# Eason Blog 一键安装 / 升级脚本（Ubuntu 22.04 / 24.04）
# 用法（在 VM 上以 sudo 运行）：
#   sudo bash /tmp/eason-blog-bundle/install.sh
# 可重复执行：已存在的 env / 数据库不会被覆盖，只更新代码与配置。
# ============================================================
set -euo pipefail

BUNDLE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=/opt/eason-blog
APP_HOME="$APP_DIR/app"
DATA_DIR="$APP_DIR/data/uploads"
WEB_DIR=/var/www/eason-blog
ENV_FILE=/etc/eason-blog.env
SERVICE=/etc/systemd/system/eason-blog.service
NGINX_SITE=/etc/nginx/sites-available/eason-blog.conf
SITE_URL="${SITE_URL:-http://$(hostname -I | awk '{print $1}')}"
ADMIN_EMAIL="${BLOG_ADMIN_EMAIL:-admin@eason.blog}"
ADMIN_PASSWORD="${BLOG_ADMIN_PASSWORD:-Admin123456}"

log() { printf '\n\033[1;32m==> %s\033[0m\n' "$*"; }

log "1/8 创建目录"
command -v curl >/dev/null 2>&1 || apt-get install -y curl >/dev/null 2>&1 || true
install -d -o eason -g eason "$APP_HOME" "$DATA_DIR"
install -d -o www-data -g www-data "$WEB_DIR" "$WEB_DIR/admin"

log "2/8 复制应用文件"
cp -f "$BUNDLE_DIR/blog-server.jar" "$APP_HOME/blog-server.jar"
rm -rf "$WEB_DIR"/*
# 前台放在站点根，后台放在 /admin/ 子目录（nginx root 指向 $WEB_DIR）
cp -a "$BUNDLE_DIR/frontend/." "$WEB_DIR/"
cp -a "$BUNDLE_DIR/admin/." "$WEB_DIR/admin/"
chown -R www-data:www-data "$WEB_DIR"
chown -R eason:eason "$APP_DIR"

log "3/8 准备数据库账号"
if [ -f "$ENV_FILE" ]; then
  # 升级：沿用已有密码
  DB_PASSWORD="$(grep -E '^DB_PASSWORD=' "$ENV_FILE" | cut -d= -f2- || true)"
  JWT_SECRET="$(grep -E '^JWT_SECRET=' "$ENV_FILE" | cut -d= -f2- || true)"
fi
DB_PASSWORD="${DB_PASSWORD:-$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)}"
JWT_SECRET="${JWT_SECRET:-$(openssl rand -base64 48)}"

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS eason_blog
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'blog'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER 'blog'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON eason_blog.* TO 'blog'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

log "4/8 写入环境变量 $ENV_FILE"
cat > "$ENV_FILE" <<ENV
# Eason Blog 运行环境（含密钥，权限 600，勿提交到仓库）
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080

DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=eason_blog
DB_USER=blog
DB_PASSWORD=${DB_PASSWORD}

JWT_SECRET=${JWT_SECRET}
JWT_EXPIRE_MINUTES=120
# 纯 HTTP 部署（内网 IP）必须为 false，否则浏览器不会带上 Cookie
COOKIE_SECURE=false
COOKIE_SAME_SITE=Lax

CORS_ALLOWED_ORIGINS=${SITE_URL}
UPLOAD_DIR=${DATA_DIR}

# 首次启动创建管理员；创建成功后可删除这两行
BLOG_ADMIN_EMAIL=${ADMIN_EMAIL}
BLOG_ADMIN_PASSWORD=${ADMIN_PASSWORD}
BLOG_ADMIN_NICKNAME=Eason
ENV
chmod 600 "$ENV_FILE"

log "5/8 安装 systemd 服务"
cp -f "$BUNDLE_DIR/eason-blog.service" "$SERVICE"
systemctl daemon-reload
systemctl enable eason-blog >/dev/null 2>&1 || true
systemctl restart eason-blog

log "6/8 配置 Nginx"
cp -f "$BUNDLE_DIR/eason-blog.conf" "$NGINX_SITE"
ln -sf "$NGINX_SITE" /etc/nginx/sites-enabled/eason-blog.conf
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

log "7/8 等待后端启动（最多 90 秒）"
for i in $(seq 1 45); do
  if curl -fsS http://127.0.0.1:8080/actuator/health >/dev/null 2>&1; then
    echo "后端已就绪"
    break
  fi
  sleep 2
  if [ "$i" = 45 ]; then
    echo "后端启动超时，最近日志："
    journalctl -u eason-blog -n 60 --no-pager
    exit 1
  fi
done

log "8/8 完成"
echo "站点：      ${SITE_URL}/"
echo "后台：      ${SITE_URL}/admin/"
echo "健康检查：  ${SITE_URL}/actuator/health（仅本机可访问）"
echo "管理员：    ${ADMIN_EMAIL} / ${ADMIN_PASSWORD}"
echo "环境变量：  ${ENV_FILE}"
echo "服务管理：  systemctl status|restart|stop eason-blog"
