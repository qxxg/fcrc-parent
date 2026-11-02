#!/usr/bin/env bash
# FCRC 一键部署脚本（在目标 Ubuntu 服务器的项目 deploy 目录执行）。
#
# 做的事：
#   1. 检查 docker / docker compose；
#   2. 首次运行时从 .env.example 生成 .env；
#   3. 构建镜像并启动整套服务；
#   4. 轮询网关/前端健康检查，失败时打印最近日志。
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${DEPLOY_DIR}"

log()  { printf '\033[1;36m[FCRC]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FCRC][ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "未安装 Docker，请先执行 scripts/bootstrap-ubuntu.sh"
docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose v2 插件"

if [[ ! -f .env ]]; then
  cp .env.example .env
  log "已生成 deploy/.env，请尽快修改 MySQL 密码与 DASHSCOPE_API_KEY（如使用真实模型）"
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

log "校验 Compose 配置…"
docker compose --env-file .env -f docker-compose.yml config -q

log "构建镜像（首次构建需要下载 Maven 依赖，通常 5-15 分钟）…"
docker compose --env-file .env -f docker-compose.yml build --pull

log "启动服务…"
docker compose --env-file .env -f docker-compose.yml up -d --remove-orphans

GATEWAY_PORT="${GATEWAY_PORT:-8080}"
WEB_PORT="${WEB_PORT:-8088}"
deadline=$((SECONDS + 600))

log "等待网关与前端就绪（最多 10 分钟）…"
while (( SECONDS < deadline )); do
  if curl -fsS "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" >/dev/null 2>&1 \
     && curl -fsS "http://127.0.0.1:${WEB_PORT}/healthz" >/dev/null 2>&1; then
    log "部署成功。"
    docker compose --env-file .env -f docker-compose.yml ps
    printf '\n驾驶舱: http://<服务器IP>:%s\n网关:   http://<服务器IP>:%s\nNacos:  http://<服务器IP>:%s/nacos\n' \
      "${WEB_PORT}" "${GATEWAY_PORT}" "${NACOS_CONSOLE_PORT:-8849}"
    exit 0
  fi
  sleep 8
done

docker compose --env-file .env -f docker-compose.yml ps || true
docker compose --env-file .env -f docker-compose.yml logs --tail=120 fcrc-gateway fcrc-agent fcrc-web || true
fail "健康检查超时，请根据上方日志排查"
