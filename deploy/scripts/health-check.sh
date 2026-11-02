#!/usr/bin/env bash
# 端到端健康检查：从 Nginx/网关一直检查到 Agent 与各业务服务。
set -Eeuo pipefail

DEPLOY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${DEPLOY_DIR}"

if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

GATEWAY_PORT="${GATEWAY_PORT:-8080}"
WEB_PORT="${WEB_PORT:-8088}"
BASE="http://127.0.0.1:${GATEWAY_PORT}"

check() {
  local name="$1" url="$2"
  local code
  code="$(curl -s -o /dev/null -w '%{http_code}' --max-time 15 "${url}" || true)"
  if [[ "${code}" == "200" ]]; then
    printf '  [OK]   %-24s %s\n' "${name}" "${url}"
  else
    printf '  [FAIL] %-24s HTTP %s  %s\n' "${name}" "${code}" "${url}"
    return 1
  fi
}

echo "FCRC 健康检查"
check "前端 Nginx"        "http://127.0.0.1:${WEB_PORT}/healthz"
check "网关 actuator"     "${BASE}/actuator/health"
check "Agent"             "${BASE}/api/agent/health"
check "客户服务"          "${BASE}/api/customers/stats"
check "风险服务"          "${BASE}/api/risk/stats"
check "合规服务"          "${BASE}/api/compliance/stats"
check "工作流服务"        "${BASE}/api/workflows/stats"
check "案件服务"          "${BASE}/api/cases/stats"
check "报告服务"          "${BASE}/api/reports/stats"

echo "全部通过。"
