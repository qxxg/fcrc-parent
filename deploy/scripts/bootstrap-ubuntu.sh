#!/usr/bin/env bash
# Ubuntu 22.04/24.04 安装 Docker Engine + Compose 插件。
#
# 用法：
#   sudo bash deploy/scripts/bootstrap-ubuntu.sh
#   sudo DOCKER_REGISTRY_MIRROR=https://docker.m.daocloud.io bash deploy/scripts/bootstrap-ubuntu.sh
#
# 设计说明：
#   1. 优先使用 Docker 官方 APT 源；如果 download.docker.com 不可达（很多内网/国内环境如此），
#      自动回退到 Ubuntu 官方仓库的 docker.io + docker-compose-v2。
#   2. 写入 registry-mirrors。没有镜像加速时，Docker Hub 往往直接超时，
#      导致 compose build/pull 卡死。默认使用 DaoCloud 镜像，可用环境变量覆盖。
#   3. 全部步骤幂等：重复执行不会破坏已有配置。
set -Eeuo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "请使用 sudo 执行：sudo bash deploy/scripts/bootstrap-ubuntu.sh" >&2
  exit 1
fi

MIRROR="${DOCKER_REGISTRY_MIRROR:-https://docker.m.daocloud.io}"
log() { printf '\033[1;36m[docker]\033[0m %s\n' "$*"; }

apt-get update -qq
apt-get install -y -qq ca-certificates curl gnupg

# ---------------------------------------------------------------- 安装 Docker
install_from_docker_repo() {
  log "尝试 Docker 官方 APT 源…"
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL --max-time 20 https://download.docker.com/linux/ubuntu/gpg \
    -o /etc/apt/keyrings/docker.asc || return 1
  chmod a+r /etc/apt/keyrings/docker.asc

  # shellcheck disable=SC1091
  . /etc/os-release
  printf 'deb [arch=%s signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu %s stable\n' \
    "$(dpkg --print-architecture)" "${VERSION_CODENAME}" \
    > /etc/apt/sources.list.d/docker.list

  apt-get update -qq || return 1
  apt-get install -y -qq docker-ce docker-ce-cli containerd.io \
    docker-buildx-plugin docker-compose-plugin || return 1
  return 0
}

install_from_ubuntu_repo() {
  log "回退到 Ubuntu 官方仓库（docker.io + docker-compose-v2）…"
  apt-get install -y -qq docker.io docker-compose-v2
}

if install_from_docker_repo; then
  log "已从 Docker 官方源安装。"
else
  rm -f /etc/apt/sources.list.d/docker.list
  install_from_ubuntu_repo
  log "已从 Ubuntu 仓库安装。"
fi

# ---------------------------------------------------------------- 镜像加速
if [[ ! -f /etc/docker/daemon.json ]]; then
  log "写入镜像加速：${MIRROR}"
  mkdir -p /etc/docker
  # 用 python/printf 生成合法 JSON，避免手写引号出错导致 dockerd 启动失败。
  printf '{"registry-mirrors":["%s"],"log-driver":"json-file","log-opts":{"max-size":"50m","max-file":"3"}}\n' \
    "${MIRROR}" > /etc/docker/daemon.json
  chmod 644 /etc/docker/daemon.json
else
  log "/etc/docker/daemon.json 已存在，保持原配置不动。"
fi

# ---------------------------------------------------------------- 启动服务
systemctl daemon-reload
systemctl enable docker
systemctl reset-failed docker.service docker.socket 2>/dev/null || true
systemctl start docker

if ! systemctl is-active --quiet docker; then
  echo "docker.service 启动失败，最近日志：" >&2
  journalctl -u docker --no-pager -n 40 >&2 || true
  exit 1
fi

if [[ -n "${SUDO_USER:-}" ]]; then
  usermod -aG docker "${SUDO_USER}"
  log "已将 ${SUDO_USER} 加入 docker 组，请重新登录（或 newgrp docker）后生效。"
fi

docker --version
docker compose version
log "完成。验证：docker info --format '{{json .RegistryConfig.Mirrors}}'"
