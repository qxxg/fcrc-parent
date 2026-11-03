# FCRC 部署目录说明

这个目录只放“部署相关”的东西，业务代码不在这里。

```text
deploy/
├── Dockerfile                   源码构建镜像（容器内 Maven 编译）
├── Dockerfile.prebuilt          预构建镜像（直接复制 target/*.jar）
├── docker-compose.yml           完整拓扑：MySQL + Nacos + 8 个服务 + Nginx
├── docker-compose.prebuilt.yml  覆盖文件：把 build 指向预构建 Dockerfile
├── .env.example                 环境变量模板
├── mysql/init/                  MySQL 首次启动初始化脚本
│   ├── 01-database.sql
│   ├── 02-schema.sql
│   └── 03-demo-data.sql
├── nginx/                       前端镜像与反向代理配置
│   ├── Dockerfile
│   └── default.conf
└── scripts/
    ├── bootstrap-ubuntu.sh      安装 Docker + Compose + 镜像加速
    ├── deploy.sh                构建并启动（支持 --prebuilt）
    ├── health-check.sh          端到端健康检查
    ├── backup.sh                MySQL 备份
    ├── rollback.sh              镜像/数据库回滚
    └── deploy-remote.ps1        Windows 一键上传部署
```

---

## 最短路径

在开发机项目根目录：

```bash
mvn -B -ntp test
mvn -B -ntp -DskipTests package
```

上传并部署：

```powershell
powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1 -Prebuilt
```

服务器上：

```bash
cd /opt/fcrc/deploy
bash scripts/deploy.sh --prebuilt
bash scripts/health-check.sh
```

访问：

- 驾驶舱 `http://<服务器IP>:8088`
- 网关 `http://<服务器IP>:8080`
- Nacos `http://<服务器IP>:8849/`

---

## 两种构建模式

```bash
# 预构建（推荐）：服务器只组装镜像，1-3 分钟
bash scripts/deploy.sh --prebuilt

# 源码构建：容器内跑 Maven，首次 15-40 分钟
bash scripts/deploy.sh
```

预构建模式要求各模块 `target/*.jar` 已存在，脚本会提前检查并给出明确提示。

---

## 常用命令

```bash
cd /opt/fcrc/deploy
COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prebuilt.yml"

$COMPOSE ps
$COMPOSE logs -f --tail=200 fcrc-gateway
$COMPOSE restart fcrc-workflow
$COMPOSE down            # 停止，保留数据
$COMPOSE down -v         # 停止并删除数据（危险）
```

备份与回滚：

```bash
bash scripts/backup.sh
bash scripts/rollback.sh 1.0.0
bash scripts/rollback.sh          # 从最近备份恢复数据库
```

---

## 关键约定

1. **`.env` 不入库**：包含数据库密码和可能的 API Key，权限设为 `600`。
2. **只有 4 个入口对外**：8088（Nginx）、8080（网关）、8848/9849（Nacos）、3306（仅回环）。
3. **业务端口 8081–8087 不映射**：只在 `fcrc-net` 内部通信。
4. **`/internal/**` 不经过网关**：内部接口只允许服务间直连。
5. **演示数据由 `FCRC_DEMO_DATA_ENABLED` 控制**：生产必须设为 `false`。
6. **Nacos 先于网关启动**：网关用 `lb://` 做服务发现。
7. **Nginx 必须关闭 SSE 缓冲**：否则 `/api/agent/chat/stream` 会“卡住不输出”。

---

## 排错入口

```bash
docker compose ps
docker inspect --format '{{json .State.Health}}' fcrc-customer
docker compose logs --tail=100 fcrc-customer
curl -v http://127.0.0.1:8080/actuator/health
```

完整排错手册见 `../docs/06-deployment.md` 第 11 节。
