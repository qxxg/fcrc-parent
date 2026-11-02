# FCRC 从 Windows 开发机部署到 Ubuntu 服务器。
#
# 前置条件：
#   * Windows 已启用 OpenSSH Client（ssh/scp/tar 可用）；
#   * 目标服务器已安装 Docker（可先执行 deploy/scripts/bootstrap-ubuntu.sh）；
#   * 目标用户已加入 docker 组，或部署时使用 sudo。
#
# 用法（会提示输入 SSH 密码，不在脚本中保存密码）：
#   powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1
#   powershell -ExecutionPolicy Bypass -File deploy/scripts/deploy-remote.ps1 -Server 192.168.3.91 -User wy
param(
    [string]$Server = "192.168.3.91",
    [string]$User = "wy",
    [string]$RemoteDir = "/opt/fcrc"
)

$ErrorActionPreference = "Stop"
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$archive = Join-Path $env:TEMP "fcrc-$stamp.tar.gz"

Write-Host "[FCRC] 打包源码：$repoRoot" -ForegroundColor Cyan
tar -czf $archive `
    --exclude="*/target" `
    --exclude="*/data" `
    --exclude=".git" `
    --exclude=".idea" `
    --exclude="deploy/backups" `
    --exclude="*.log" `
    -C $repoRoot .

if ($LASTEXITCODE -ne 0) { throw "tar 打包失败" }

$target = "$User@$Server"
Write-Host "[FCRC] 创建远程目录 $RemoteDir" -ForegroundColor Cyan
ssh $target "sudo mkdir -p '$RemoteDir' && sudo chown -R `$(id -u):`$(id -g) '$RemoteDir'"

Write-Host "[FCRC] 上传部署包" -ForegroundColor Cyan
scp $archive "${target}:/tmp/fcrc-deploy.tar.gz"

Write-Host "[FCRC] 解压并启动" -ForegroundColor Cyan
ssh $target "tar -xzf /tmp/fcrc-deploy.tar.gz -C '$RemoteDir' && cd '$RemoteDir/deploy' && if [ ! -f .env ]; then cp .env.example .env; fi && bash scripts/deploy.sh"

Remove-Item $archive -ErrorAction SilentlyContinue
Write-Host "[FCRC] 完成。驾驶舱：http://${Server}:8088  网关：http://${Server}:8080" -ForegroundColor Green
