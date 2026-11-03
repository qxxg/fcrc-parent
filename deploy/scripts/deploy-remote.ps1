# FCRC 从 Windows 开发机部署到 Ubuntu 服务器。
#
# 前置条件：
#   * Windows 已启用 OpenSSH Client（ssh/scp/tar 可用）；
#   * 目标服务器已安装 Docker（可先执行 sudo bash deploy/scripts/bootstrap-ubuntu.sh）；
#   * 目标用户已加入 docker 组，或部署时使用 sudo。
#
# 用法（会提示输入 SSH 密码，不在脚本中保存密码）：
#   powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1 -Prebuilt
#   powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1
#
# -Prebuilt：本机先执行 mvn -DskipTests package，打包时保留 target/*.jar，
#            服务器不跑 Maven，部署更快、更稳定（推荐）。
# 不带参数：打包时排除 target，服务器容器内用 Maven 构建。
param(
    [string]$Server = "192.168.3.91",
    [string]$User = "wy",
    [string]$RemoteDir = "/opt/fcrc",
    [switch]$Prebuilt
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$archive = Join-Path $env:TEMP "fcrc-$stamp.tar"

$tarArgs = @(
    "-cf", $archive,
    "--exclude=.git",
    "--exclude=.idea",
    "--exclude=.vscode",
    "--exclude=*/data",
    "--exclude=deploy/backups",
    "--exclude=deploy/.env",
    "--exclude=*.log",
    "-C", $repoRoot, "."
)

if ($Prebuilt) {
    Write-Host "[FCRC] 预构建模式：保留 target/*.jar" -ForegroundColor Cyan
    $tarArgs += @(
        "--exclude=*/target/classes",
        "--exclude=*/target/test-classes",
        "--exclude=*/target/surefire-reports",
        "--exclude=*/target/maven-status",
        "--exclude=*/target/generated-sources",
        "--exclude=*/target/generated-test-sources",
        "--exclude=*/target/test-agent-workspace",
        "--exclude=*/target/test-agent-state"
    )
    foreach ($m in @("fcrc-customer","fcrc-risk","fcrc-compliance","fcrc-case","fcrc-report","fcrc-workflow","fcrc-agent","fcrc-gateway")) {
        $jar = Join-Path $repoRoot "$m\target\$m-1.0.0.jar"
        if (-not (Test-Path $jar)) {
            throw "缺少 $jar，请先在项目根目录执行：mvn -B -DskipTests package"
        }
    }
} else {
    Write-Host "[FCRC] 源码构建模式：排除 target" -ForegroundColor Cyan
    $tarArgs += "--exclude=*/target"
}

Write-Host "[FCRC] 打包：$repoRoot" -ForegroundColor Cyan
tar @tarArgs
if ($LASTEXITCODE -ne 0) { throw "tar 打包失败" }
Write-Host ("[FCRC] 包大小 {0:N1} MB" -f ((Get-Item $archive).Length / 1MB))

$target = "$User@$Server"

Write-Host "[FCRC] 创建远程目录 $RemoteDir" -ForegroundColor Cyan
ssh $target "sudo mkdir -p '$RemoteDir' && sudo chown -R `$(id -u):`$(id -g) '$RemoteDir'"

Write-Host "[FCRC] 上传部署包" -ForegroundColor Cyan
scp $archive "${target}:/tmp/fcrc-deploy.tar"

$mode = if ($Prebuilt) { "--prebuilt" } else { "" }
Write-Host "[FCRC] 解压并启动" -ForegroundColor Cyan
ssh $target "tar -xf /tmp/fcrc-deploy.tar -C '$RemoteDir' && cd '$RemoteDir/deploy' && if [ ! -f .env ]; then cp .env.example .env; chmod 600 .env; fi && bash scripts/deploy.sh $mode"

Remove-Item $archive -ErrorAction SilentlyContinue
Write-Host "[FCRC] 完成。驾驶舱：http://${Server}:8088  网关：http://${Server}:8080" -ForegroundColor Green
