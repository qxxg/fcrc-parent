#!/usr/bin/env bash
# FCRC 一键部署脚本（在目标 Ubuntu 服务器的项目 deploy 目录执行）。
#
# 用法：
#   ./scripts/deploy.sh              # 源码模式：在容器内用 Maven 构建镜像（首次很慢）
#   ./scripts/deploy.sh --prebuilt   # 预构建模式：直接使用各模块 target/*.jar（推荐）
#
# 做的事：
#   1. 检查 docker / docker compose；
#   2. 首次运行时从 .env.example 生成 .env；
#   3. 构建镜像并启动整套服务；
#   4. 轮询网关/前端健康检查，失败时打印最近日志。
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${DEPLOY_DIR}"

MODE="source"
if [[ "${1:-}" == "--prebuilt" ]]; then
  MODE="prebuilt"
elif [[ $# -gt 0 ]]; then
  echo "未知参数：$1（可用：--prebuilt）" >&2
  exit 2
fi

log()  { printf '\033[1;36m[FCRC]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[FCRC][ERROR]\033[0m %s\n' "$*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || fail "未安装 Docker，请先执行 sudo bash scripts/bootstrap-ubuntu.sh"
docker compose version >/dev/null 2>&1 || fail "未安装 Docker Compose v2 插件"

if [[ ! -f .env ]]; then
  cp .env.example .env
  log "已生成 deploy/.env，请尽快修改 MySQL 密码与 DASHSCOPE_API_KEY（如使用真实模型）"
fi

set -a
# shellcheck disable=SC1091
source .env
set +a

COMPOSE=(docker compose --env-file .env -f docker-compose.yml)
if [[ "${MODE}" == "prebuilt" ]]; then
  COMPOSE+=(-f docker-compose.prebuilt.yml)
  # 预构建模式要求 jar 已经存在，提前给出明确错误，而不是让 docker build 报 COPY 失败。
  for module in fcrc-customer fcrc-risk fcrc-compliance fcrc-case fcrc-report \
                fcrc-workflow fcrc-agent fcrc-gateway; do
    compgen -G "../${module}/target/*.jar" >/dev/null \
      || fail "缺少 ${module}/target/*.jar，请先在项目根目录执行 mvn -B -DskipTests package"
  done
fi

log "部署模式：${MODE}"
log "校验 Compose 配置…"
"${COMPOSE[@]}" config -q

if [[ "${MODE}" == "prebuilt" ]]; then
  log "构建镜像（使用已构建的 jar，通常 1-3 分钟）…"
  "${COMPOSE[@]}" build
else
  log "构建镜像（容器内 Maven 构建，首次可能 15-40 分钟）…"
  "${COMPOSE[@]}" build --pull
fi

log "启动服务…"
"${COMPOSE[@]}" up -d --remove-orphans

GATEWAY_PORT="${GATEWAY_PORT:-8080}"
WEB_PORT="${WEB_PORT:-8088}"
deadline=$((SECONDS + 900))

log "等待网关与前端就绪（最多 15 分钟）…"
while (( SECONDS < deadline )); do
  if curl -fsS "http://127.0.0.1:${GATEWAY_PORT}/actuator/health" >/dev/null 2>&1 \
     && curl -fsS "http://127.0.0.1:${WEB_PORT}/healthz" >/dev/null 2>&1; then
    log "部署成功。"
    "${COMPOSE[@]}" ps
    printf '\n驾驶舱: http://<服务器IP>:%s\n网关:   http://<服务器IP>:%s\nNacos:  http://<服务器IP>:%s/\n' \
      "${WEB_PORT}" "${GATEWAY_PORT}" "${NACOS_CONSOLE_PORT:-8849}"
    exit 0
  fi
  sleep 8
done

"${COMPOSE[@]}" ps || true
"${COMPOSE[@]}" logs --tail=120 fcrc-gateway fcrc-agent fcrc-web || true
fail "健康检查超时，请根据上方日志排查"
