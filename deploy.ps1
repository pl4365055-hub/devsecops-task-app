# deploy.ps1 — deploy.sh 的 PowerShell 版本
# 用法：先設定環境變數 REGISTRY、REGISTRY_USERNAME、REGISTRY_PASSWORD、IMAGE_TAG 再執行
#       pwsh -File .\deploy.ps1

$ErrorActionPreference = 'Stop'  # 任何命令失敗則停止（對應 set -e）

# 原生程式（docker、curl.exe）失敗預設不會丟例外，用此函式檢查結束碼
function Assert-NativeSuccess {
    param([string]$Step)
    if ($LASTEXITCODE -ne 0) {
        throw "步驟失敗（結束碼 $LASTEXITCODE）：$Step"
    }
}

Write-Host "=== Deploying Task App ==="
Write-Host "Image tag: $env:IMAGE_TAG"

# 1. 登入 Registry（用 --password-stdin 避免密碼出現在程序清單）
$env:REGISTRY_PASSWORD | docker login $env:REGISTRY -u $env:REGISTRY_USERNAME --password-stdin
Assert-NativeSuccess "docker login"

# 2. 拉取最新映像
docker compose -f docker-compose.prod.yml pull
Assert-NativeSuccess "compose pull"

# 3. 零停機重啟（先啟動新容器，再停止舊容器）
docker compose -f docker-compose.prod.yml up -d --remove-orphans
Assert-NativeSuccess "compose up"

# 4. 等待健康檢查
Write-Host "Waiting for services to be healthy..."
Start-Sleep -Seconds 10

# 5. 驗證（用 curl.exe，避免打到 PowerShell 的 curl 別名 Invoke-WebRequest）
curl.exe -sf http://localhost:8080/actuator/health -o NUL
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Backend is healthy"
} else {
    Write-Host "❌ Backend health check failed"
    docker compose -f docker-compose.prod.yml logs backend
    exit 1
}

# 6. 清理舊映像
docker image prune -f
Assert-NativeSuccess "image prune"

Write-Host "=== Deployment complete ==="
