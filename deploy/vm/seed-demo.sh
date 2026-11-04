#!/usr/bin/env bash
# ============================================================
# 为生产库导入演示文章（幂等：已存在的 slug 不会重复插入）
# 用法：sudo bash seed-demo.sh
# 原理：临时打开 app.demo-data.enabled，让 DemoDataSeeder 运行一次，
#       成功后再关掉，避免以后每次重启都重新检查。
# ============================================================
set -euo pipefail

ENV_FILE=/etc/eason-blog.env
APP_URL=http://127.0.0.1:8080/actuator/health

if [ "$(id -u)" -ne 0 ]; then
  echo "请用 sudo 运行：sudo bash $0"
  exit 1
fi

wait_healthy() {
  for _ in $(seq 1 60); do
    if curl -fsS "$APP_URL" >/dev/null 2>&1; then return 0; fi
    sleep 2
  done
  echo "后端启动超时，请查看：journalctl -u eason-blog -n 80 --no-pager"
  return 1
}

cp -a "$ENV_FILE" "$ENV_FILE.bak.$(date +%Y%m%d%H%M%S)"
if grep -q '^APP_DEMODATA_ENABLED=' "$ENV_FILE"; then
  sed -i 's/^APP_DEMODATA_ENABLED=.*/APP_DEMODATA_ENABLED=true/' "$ENV_FILE"
else
  echo 'APP_DEMODATA_ENABLED=true' >> "$ENV_FILE"
fi

echo '==> 重启后端并等待就绪（导入演示数据）'
systemctl restart eason-blog
wait_healthy

echo '==> 导入结果'
mysql -N -e "SELECT CONCAT('articles=', COUNT(*)) FROM eason_blog.blog_article;"
mysql -N -e "SELECT CONCAT('categories=', COUNT(*)) FROM eason_blog.blog_category;"
mysql -N -e "SELECT CONCAT('tags=', COUNT(*)) FROM eason_blog.blog_tag;"
mysql -N -e "SELECT CONCAT('columns=', COUNT(*)) FROM eason_blog.blog_column;"

sed -i 's/^APP_DEMODATA_ENABLED=.*/APP_DEMODATA_ENABLED=false/' "$ENV_FILE"
echo '==> 关闭演示数据开关并重启'
systemctl restart eason-blog
wait_healthy

echo '==> 完成，当前文章数：'
mysql -N -e "SELECT COUNT(*) FROM eason_blog.blog_article;"
