#!/usr/bin/env bash
# 容器每次启动后执行：拉起后端与两个前端（幂等，已在跑就跳过）
set -euo pipefail

cd "$(dirname "$0")/.."
mkdir -p /tmp/eason-blog

wait_for() {
  local host=$1 port=$2 name=$3
  for _ in $(seq 1 60); do
    if (echo >"/dev/tcp/${host}/${port}") >/dev/null 2>&1; then
      echo "==> ${name} 已就绪"
      return 0
    fi
    sleep 2
  done
  echo "!! ${name} 等待超时"
  return 1
}

if pgrep -f "blog-server.*spring-boot:run" >/dev/null 2>&1; then
  echo "==> 后端已在运行"
else
  echo "==> 启动后端（日志 /tmp/eason-blog/backend.log）"
  (cd blog-server && nohup mvn -q -DskipTests spring-boot:run >/tmp/eason-blog/backend.log 2>&1 &)
fi
wait_for 127.0.0.1 8080 "后端" || tail -n 40 /tmp/eason-blog/backend.log

if pgrep -f "vite.*blog-frontend" >/dev/null 2>&1; then
  echo "==> 前台已在运行"
else
  echo "==> 启动前台"
  (cd blog-frontend && nohup pnpm dev >/tmp/eason-blog/frontend.log 2>&1 &)
fi

if pgrep -f "vite.*blog-admin" >/dev/null 2>&1; then
  echo "==> 后台已在运行"
else
  echo "==> 启动后台"
  (cd blog-admin && nohup pnpm dev >/tmp/eason-blog/admin.log 2>&1 &)
fi

wait_for 127.0.0.1 5173 "前台"
wait_for 127.0.0.1 5174 "后台"

echo ""
echo "完成："
echo "  前台  http://localhost:5173"
echo "  后台  http://localhost:5174   (admin@eason.blog / Admin123456)"
echo "  接口  http://localhost:8080/actuator/health"
echo "把「端口」面板里 5173 的可见性设为 Public，即可得到别人可访问的 https 地址。"
