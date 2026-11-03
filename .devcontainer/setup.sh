#!/usr/bin/env bash
# Codespaces 创建后执行一次：装依赖（Java/Maven 由基础镜像提供）
set -euo pipefail

cd "$(dirname "$0")/.."

echo "==> 启用 pnpm"
if ! command -v pnpm >/dev/null 2>&1; then
  corepack enable || true
  corepack prepare pnpm@11 --activate || npm install -g pnpm@11
fi

echo "==> 安装前台依赖"
(cd blog-frontend && pnpm install --frozen-lockfile)

echo "==> 安装后台依赖"
(cd blog-admin && pnpm install --frozen-lockfile)

echo "==> 预热后端依赖（首次启动会下载 Maven 依赖）"
(cd blog-server && mvn -B -q -DskipTests dependency:go-offline || true)

echo "初始化完成。容器启动后会自动运行 .devcontainer/start.sh"
