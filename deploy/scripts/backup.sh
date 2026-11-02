#!/usr/bin/env bash
# 备份 MySQL 全库到 deploy/backups/fcrc-时间戳.sql.gz。
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${DEPLOY_DIR}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

mkdir -p backups
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="backups/fcrc-${STAMP}.sql.gz"

docker compose --env-file .env -f docker-compose.yml exec -T fcrc-mysql \
  sh -c 'exec mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" \
         --single-transaction --routines --triggers --set-gtid-purged=OFF "$MYSQL_DATABASE"' \
  | gzip -9 > "${TARGET}"

echo "备份完成：${DEPLOY_DIR}/${TARGET}"
