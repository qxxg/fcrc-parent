#!/usr/bin/env bash
# 回滚脚本。
#
# 用法：
#   ./rollback.sh <上一个镜像TAG>   回到上一个镜像版本（要求该 TAG 的镜像仍在本机）
#   ./rollback.sh                    从 backups 中最近一次备份恢复数据库并重启
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${DEPLOY_DIR}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

if [[ $# -ge 1 ]]; then
  TAG="$1"
  echo "切换镜像 TAG -> ${TAG}，并重启服务…"
  IMAGE_TAG="${TAG}" docker compose --env-file .env -f docker-compose.yml up -d --no-build --remove-orphans
  echo "回滚完成。验证：curl -fsS http://127.0.0.1:${GATEWAY_PORT:-8080}/actuator/health"
  exit 0
fi

LATEST="$(ls -1t backups/fcrc-*.sql.gz 2>/dev/null | head -n1 || true)"
if [[ -z "${LATEST}" ]]; then
  echo "没有找到数据库备份，也没有指定镜像 TAG。" >&2
  echo "用法：$0 <IMAGE_TAG> | $0" >&2
  exit 1
fi

echo "将使用 ${LATEST} 覆盖当前数据库。"
read -r -p "确认继续？输入 YES: " CONFIRM
[[ "${CONFIRM}" == "YES" ]] || { echo "已取消"; exit 1; }

gunzip -c "${LATEST}" | docker compose --env-file .env -f docker-compose.yml exec -T fcrc-mysql \
  sh -c 'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"'

docker compose --env-file .env -f docker-compose.yml up -d --remove-orphans
echo "数据库已恢复并重启服务。"
