# 06 · 部署手册

> 本文档描述**实际可执行的部署路径**，不是在纸上写一套理想架构。文末的“已知环境限制”记录了本次部署中真实遇到的问题与解决方式。

---

## 1. 目标环境

本次部署使用的服务器：

| 项 | 值 |
| --- | --- |
| 地址 | `192.168.3.91` |
| 系统 | Ubuntu（内核 `7.0.0-42-generic`，x86_64） |
| 资源 | 7.4 GB 内存 / 48 GB 磁盘（可用约 39 GB） |
| 登录用户 | `wy`（具备 sudo 与 docker 组权限） |
| 部署目录 | `/opt/fcrc` |
| 编排 | Docker Engine 29.1.3 + Compose v2.40.3 |

对服务器的要求：

- 2 核 4 GB 以上；本平台 9 个 Java 容器 + MySQL + Nacos，建议 8 GB；
- 能访问 Ubuntu APT 源；
- 能访问一个容器镜像加速器（见 §4）；
- 端口 `8088`、`8080`、`8848`、`8849` 可用。

---

## 2. 部署拓扑

```text
                 ┌──────────────────────────────┐
   局域网  ─────► │ fcrc-web (Nginx)  :8088      │
                 │  ├─ /        静态驾驶舱       │
                 │  └─ /api/    反向代理         │
                 └──────────────┬───────────────┘
                                │ HTTP（SSE 不缓冲）
                 ┌──────────────▼───────────────┐
                 │ fcrc-gateway :8080            │
                 │  Spring Cloud Gateway         │
                 │  lb:// + Nacos 服务发现        │
                 └───┬───────┬───────┬───────────┘
        ┌────────────┘       │       └─────────────┐
        ▼                    ▼                     ▼
  fcrc-customer        fcrc-risk            fcrc-compliance
     :8081                :8082                  :8083
        │                    │                     │
        └────────┬───────────┴──────────┬──────────┘
                 ▼                      ▼
          fcrc-workflow :8084     fcrc-agent :8085
                 │                      │
                 ├──► fcrc-case :8086   │
                 └──► fcrc-report :8087 │
                                        │
                 ┌──────────────────────┘
                 ▼
        fcrc-mysql :3306（仅 127.0.0.1 映射）
        fcrc-nacos :8848 / :9848 / 控制台 :8849
```

要点：

- 只有 `fcrc-web`、`fcrc-gateway`、`fcrc-nacos`、`fcrc-mysql`（回环）映射到宿主机；
- 业务服务只在 `fcrc-net` 内部网络暴露，通过服务名互相访问；
- 网关用 `lb://fcrc-xxx` 走 Nacos 服务发现，因此必须先启动 Nacos；
- `/internal/**` **不在网关路由中**，只能由内网服务直连。

---

## 3. 镜像与构建策略

项目提供两种镜像构建方式，对应两个 Dockerfile：

| 方式 | Dockerfile | 适用场景 | 耗时 |
| --- | --- | --- | --- |
| 源码构建 | `deploy/Dockerfile` | CI、一次构建、追求“源码到镜像”可复现 | 首次 15–40 分钟 |
| 预构建 jar | `deploy/Dockerfile.prebuilt` | 内网服务器、已在本机跑过 `mvn package` | 1–3 分钟 |

源码构建使用多阶段镜像：`maven:3.9-eclipse-temurin-21` 编译，`eclipse-temurin:21-jre-jammy` 运行。预构建方式跳过 Maven，直接把 `target/*.jar` 复制进 JRE 镜像。

> **为什么推荐预构建？**
> 9 个服务各自在容器里跑一次 Maven，会重复下载同一批依赖；服务器内存有限时并行构建容易 OOM。
> 预构建还能保证“部署的就是测试通过的那份 jar”，避免环境差异。

两种方式的运行镜像一致：

- 非 root 用户 `fcrc`；
- 只安装 JRE + curl（curl 仅用于健康检查）；
- `-XX:MaxRAMPercentage=75` 适配容器内存限制；
- 时区 `Asia/Shanghai`、UTF-8。

---

## 4. 服务器准备

### 4.1 安装 Docker

```bash
sudo bash deploy/scripts/bootstrap-ubuntu.sh
```

脚本行为：

1. 优先尝试 Docker 官方源 `download.docker.com`；
2. 若不可达，自动回退到 Ubuntu 仓库的 `docker.io` + `docker-compose-v2`；
3. 写入 `/etc/docker/daemon.json`（镜像加速 + 日志轮转）；
4. `systemctl enable --now docker`，并把执行用户加入 `docker` 组。

安装后重新登录（或 `newgrp docker`）使组权限生效：

```bash
docker info --format '{{.ServerVersion}} | {{json .RegistryConfig.Mirrors}}'
```

### 4.2 镜像加速

Docker Hub 在很多网络环境下不可直接访问（表现为 `curl https://registry-1.docker.io/v2/` 超时）。脚本默认写入：

```json
{
  "registry-mirrors": ["https://docker.m.daocloud.io"],
  "log-driver": "json-file",
  "log-opts": { "max-size": "50m", "max-file": "3" }
}
```

自定义：

```bash
sudo DOCKER_REGISTRY_MIRROR=https://docker.1ms.run bash deploy/scripts/bootstrap-ubuntu.sh
```

> **不要把 `/etc/docker/daemon.json` 写坏。** 该文件必须是合法 JSON，否则 `dockerd` 直接启动失败。
> 可用 `jq . /etc/docker/daemon.json` 校验。本次部署就遇到过“文件内容只有 `root`”导致服务反复重启的问题，详见 §11。

### 4.3 预拉基础镜像

```bash
docker pull nginx:1.27-alpine
docker pull eclipse-temurin:21-jre-jammy
docker pull mysql:8.4
docker pull nacos/nacos-server:v3.0.3
```

镜像拉取成功是后续构建的前提。镜像加速器对 `/v2/` 返回 `401` 是**正常现象**（要求 token），不代表不可用；以实际 `docker pull` 为准。

---

## 5. 本地构建与上传

在开发机（项目根目录）：

```bash
mvn -B -ntp test          # 先跑测试
mvn -B -ntp -DskipTests package
```

产物：

```text
fcrc-customer/target/fcrc-customer-1.0.0.jar
fcrc-risk/target/fcrc-risk-1.0.0.jar
...（共 8 个可执行 jar）
```

打包上传（排除 target 以外的大目录，但保留 jar）：

```bash
tar -cf fcrc-deploy.tar \
  --exclude=.git --exclude=.idea \
  --exclude='*/data' \
  --exclude='*/target/classes' --exclude='*/target/test-classes' \
  --exclude='*/target/surefire-reports' \
  -C . .

scp fcrc-deploy.tar wy@192.168.3.91:/tmp/
ssh wy@192.168.3.91 "sudo mkdir -p /opt/fcrc && sudo chown -R \$USER:\$USER /opt/fcrc && tar -xf /tmp/fcrc-deploy.tar -C /opt/fcrc"
```

Windows 可用 `deploy/scripts/deploy-remote.ps1`（会提示输入密码，不在脚本里保存凭据）：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1 -Prebuilt
```

---

## 6. 配置 `.env`

首次部署脚本会从 `.env.example` 复制。生产/演示环境至少修改：

```bash
cd /opt/fcrc/deploy
cp .env.example .env
chmod 600 .env
```

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `IMAGE_TAG` | `1.0.0` | 镜像标签，回滚时指定旧 tag |
| `WEB_PORT` | `8088` | 驾驶舱对外端口 |
| `GATEWAY_PORT` | `8080` | 网关对外端口 |
| `MYSQL_HOST_PORT` | `3306` | 仅绑定 `127.0.0.1` |
| `MYSQL_DATABASE` | `fcrc` | 数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `fcrc` / `fcrc_pass` | **必须修改** |
| `MYSQL_ROOT_PASSWORD` | `fcrc_root_pass` | **必须修改** |
| `NACOS_VERSION` | `v3.0.3` | Nacos 镜像标签 |
| `NACOS_CONSOLE_PORT` | `8849` | Nacos 3.x 控制台 |
| `FCRC_DEMO_DATA_ENABLED` | `true` | 演示数据开关，生产设 `false` |
| `FCRC_AGENT_MODEL` | `mock` | `mock` 或 `dashscope` |
| `DASHSCOPE_API_KEY` | 空 | `dashscope` 模式必填 |
| `FCRC_AGENT_SANDBOX` | `false` | Docker 沙箱开关 |

> `.env` 已被 `.gitignore` / `.dockerignore` 排除，不要提交到版本库。
> `MYSQL_PASSWORD` 建议用双引号包裹，避免 `#`、空格等字符被 shell 误解析。

---

## 7. 启动

```bash
cd /opt/fcrc/deploy

# 预构建 jar（推荐）
bash scripts/deploy.sh --prebuilt

# 或源码构建
bash scripts/deploy.sh
```

脚本流程：

1. 校验 `docker` / `docker compose`；
2. 无 `.env` 时自动生成；
3. `docker compose config -q` 校验配置；
4. 构建镜像 → `up -d`；
5. 最多等待 15 分钟，轮询网关与前端健康检查；
6. 失败时打印 `ps` 与最近 120 行日志。

手动等价命令：

```bash
docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml build
docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml up -d
docker compose ps
```

### 7.1 首次启动顺序

```text
fcrc-mysql (healthy)
      │
fcrc-nacos (started)
      │
业务服务 ──► 注册到 Nacos
      │
fcrc-gateway (依赖 Nacos)
      │
fcrc-web (依赖 gateway healthy)
```

- MySQL 首次启动会执行 `deploy/mysql/init/*.sql`（建库、建表、演示数据）；
- 业务服务使用 `ddl-auto: update`，Hibernate 会补齐缺失的表/列；
- 初始化 SQL 与 JPA 实体必须保持列名一致（Hibernate 默认把 camelCase 转 snake_case）。

---

## 8. 验证

```bash
cd /opt/fcrc/deploy
bash scripts/health-check.sh
```

逐项检查：

| 检查 | 地址 |
| --- | --- |
| 前端 Nginx | `http://127.0.0.1:8088/healthz` |
| 网关 | `http://127.0.0.1:8080/actuator/health` |
| Agent | `http://127.0.0.1:8080/api/agent/health` |
| 客户 | `http://127.0.0.1:8080/api/customers/stats` |
| 风险 | `http://127.0.0.1:8080/api/risk/stats` |
| 合规 | `http://127.0.0.1:8080/api/compliance/stats` |
| 工作流 | `http://127.0.0.1:8080/api/workflows/stats` |
| 案件 | `http://127.0.0.1:8080/api/cases/stats` |
| 报告 | `http://127.0.0.1:8080/api/reports/stats` |

浏览器入口：

- 驾驶舱：`http://192.168.3.91:8088`
- 网关直连：`http://192.168.3.91:8080`
- Nacos 控制台：`http://192.168.3.91:8849/`

### 8.1 端到端冒烟

```bash
# 低风险：自动通过
curl -s -X POST http://127.0.0.1:8080/api/workflows \
  -H 'Content-Type: application/json' \
  -d '{"customerId":1,"transactionAmount":80000,"transactionType":"DEPOSIT","crossBorder":false,"recentTransactionCount":2,"newRelationship":false,"highRiskProduct":false,"sourceOfFunds":"工资","businessPurpose":"储蓄","identityDocumentVerified":true,"beneficialOwnerVerified":false,"adverseMediaFlag":false}'

# 高风险：暂停等待人工复核
curl -s -X POST http://127.0.0.1:8080/api/workflows \
  -H 'Content-Type: application/json' \
  -d '{"customerId":3,"transactionAmount":2000000,"transactionType":"TRANSFER","crossBorder":true,"recentTransactionCount":40,"newRelationship":true,"highRiskProduct":true,"sourceOfFunds":"","businessPurpose":"跨境投资","identityDocumentVerified":true,"beneficialOwnerVerified":true,"adverseMediaFlag":true}'

# AI 对话
curl -s -X POST http://127.0.0.1:8080/api/agent/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"客户 1 的风险如何？","userId":"demo","sessionId":"main"}'
```

---

## 9. 运维

### 9.1 常用命令

```bash
cd /opt/fcrc/deploy
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml"

$COMPOSE ps
$COMPOSE logs -f --tail=200 fcrc-workflow
$COMPOSE restart fcrc-agent
$COMPOSE stop
$COMPOSE down                 # 保留数据卷
$COMPOSE down -v              # 删除数据卷（慎用）
```

### 9.2 数据卷

| 卷 | 内容 |
| --- | --- |
| `fcrc_mysql_data` | MySQL 数据目录 |
| `fcrc_nacos_data` | Nacos 数据 |
| `fcrc_nacos_logs` | Nacos 日志 |
| `fcrc_agent_data` | Agent 工作区与会话状态 |

### 9.3 备份

```bash
bash scripts/backup.sh
# 输出 deploy/backups/fcrc-YYYYmmdd-HHMMSS.sql.gz
```

建议加入 cron：

```cron
0 2 * * * cd /opt/fcrc/deploy && bash scripts/backup.sh >> /var/log/fcrc-backup.log 2>&1
```

### 9.4 升级

```bash
# 1. 备份
bash scripts/backup.sh

# 2. 上传新代码/jar 并解压到 /opt/fcrc（不要覆盖 .env）

# 3. 用新 tag 构建并启动
IMAGE_TAG=1.0.1 bash scripts/deploy.sh --prebuilt

# 4. 验证
bash scripts/health-check.sh
```

### 9.5 回滚

```bash
# 回到上一个可用镜像 tag（镜像需仍在本机）
bash scripts/rollback.sh 1.0.0

# 从最近一次数据库备份恢复（会二次确认）
bash scripts/rollback.sh
```

> 镜像回滚不会回滚数据库结构。若新版本执行过不兼容的 DDL，必须同时用数据库备份恢复。

---

## 10. 性能与资源

| 容器 | 建议内存 | 说明 |
| --- | --- | --- |
| MySQL | 1 GB+ | `max-connections=300` |
| Nacos | 512 MB | JVM_XMX=512m |
| 每个 Java 服务 | 256–512 MB | `MaxRAMPercentage=70` |
| 合计 | 约 6–7 GB | 8 GB 机器可跑，生产建议拆分 |

调优入口：

- `deploy/docker-compose.yml` 的 `JAVA_OPTS`；
- `deploy/.env` 的端口与密码；
- Nacos 的 `JVM_XMS/JVM_XMX`；
- MySQL 的 `command` 参数。

---

## 11. 已知环境限制与排错

以下问题均为本次部署实际遇到，处理方式已验证。

### 11.1 `docker.service` 启动失败：`invalid character 'r'`

**现象**

```text
unable to configure the Docker daemon with file /etc/docker/daemon.json:
invalid character 'r' looking for beginning of value
```

**原因**：写 `daemon.json` 的命令管道用错，文件内容变成了 `root`。

**修复**

```bash
printf '%s\n' '{"registry-mirrors":["https://docker.m.daocloud.io"]}' | sudo tee /etc/docker/daemon.json
sudo systemctl reset-failed docker.service docker.socket
sudo systemctl start docker
```

**预防**：写完用 `jq . /etc/docker/daemon.json` 或 `python3 -m json.tool` 校验。

### 11.2 Docker Hub 不可达

```text
curl https://registry-1.docker.io/v2/  → 000（超时）
curl https://docker.m.daocloud.io/v2/  → 401（正常，需 token）
```

处理：配置 `registry-mirrors` 后重试 `docker pull`。若镜像站也不可用，改用内网 Harbor 或离线导入：

```bash
docker save -o fcrc-images.tar fcrc/fcrc-gateway:1.0.0 ...
docker load -i fcrc-images.tar
```

### 11.3 `download.docker.com` 不可达

官方 APT 源超时会导致 `bootstrap-ubuntu.sh` 失败。脚本已内置回退：

```bash
sudo apt-get install -y docker.io docker-compose-v2
```

### 11.4 服务启动慢 / 健康检查失败

```bash
docker compose logs --tail=100 fcrc-customer
docker inspect --format '{{json .State.Health}}' fcrc-customer
```

常见原因：

- MySQL 尚未 ready：`depends_on` 已配置 `service_healthy`，但仍需关注首次初始化耗时；
- Nacos 注册失败：检查 `NACOS_SERVER_ADDR` 是否为 `fcrc-nacos:8848`；
- 端口冲突：修改 `.env` 中的 `*_PORT`。

### 11.5 网关 503

`lb://` 路由依赖 Nacos 中有实例。检查：

```bash
curl http://127.0.0.1:8848/nacos/v1/ns/catalog/services?namespaceId=public
docker compose logs fcrc-gateway | grep -i "no servers"
```

### 11.6 SSE 无输出

Nginx 必须关闭缓冲：

```nginx
proxy_buffering off;
proxy_read_timeout 300s;
add_header X-Accel-Buffering no;
```

已包含在 `deploy/nginx/default.conf`。

### 11.7 中文乱码

- 文件本身是 UTF-8；
- PowerShell 读取加 `-Encoding utf8`；
- 容器内设置 `TZ=Asia/Shanghai`、`-Dfile.encoding=UTF-8`。

---

## 12. 生产化清单

当前编排是**单机 Compose 演示环境**，投产前必须：

- [ ] 使用 HTTPS，Nginx/网关配置证书；
- [ ] 关闭 `8080`、`8848`、`8849` 的公网访问，只保留 Nginx；
- [ ] 开启 Nacos 认证，替换默认 token；
- [ ] MySQL 使用强密码 + 密钥管理，禁止 root 远程；
- [ ] 接入 OIDC/SSO，网关注入可信 `userId` 与角色；
- [ ] 移除或收紧 `/api/agent/permission-mode`；
- [ ] 生产设置 `FCRC_DEMO_DATA_ENABLED=false`；
- [ ] 增加资源限制（`deploy.resources.limits`）与只读根文件系统；
- [ ] 镜像扫描、SBOM、签名；
- [ ] 备份加密与定期恢复演练；
- [ ] 日志集中采集（Loki/ELK）与告警；
- [ ] 多副本 + 外部 MySQL/Nacos，或迁移到 Kubernetes。
