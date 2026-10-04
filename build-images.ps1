# build-images.ps1 — 構建不可變應用鏡像（Build Once）
# 用法：先設定環境變數 REGISTRY、IMAGE_TAG 再執行
#       $env:REGISTRY="fantasy15"; $env:IMAGE_TAG="v2"; pwsh -File .\build-images.ps1
#
# 本腳本只負責「構建並（可選）推送」鏡像，不做任何部署。
# 部署統一由 deploy.ps1 或 docker compose 引用同一個 IMAGE_TAG 完成（Deploy Many）。

$ErrorActionPreference = 'Stop'

# 預設值，方便本機 hands-on；CI 中以環境變數覆寫
if (-not $env:REGISTRY) { $env:REGISTRY = 'fantasy15' }
if (-not $env:IMAGE_TAG) { $env:IMAGE_TAG = 'v2' }

function Assert-NativeSuccess {
    param([string]$Step)
    if ($LASTEXITCODE -ne 0) {
        throw "步驟失敗（結束碼 $LASTEXITCODE）：$Step"
    }
}

$backendImage  = "$env:REGISTRY/task-app-backend:$env:IMAGE_TAG"
$frontendImage = "$env:REGISTRY/task-app-frontend:$env:IMAGE_TAG"

Write-Host "=== Building images once ==="
Write-Host "Backend : $backendImage"
Write-Host "Frontend: $frontendImage"

# 1. 構建後端鏡像（不繫結任何環境 profile，認證於運行時決定）
docker build -t $backendImage ./backend
Assert-NativeSuccess "build backend"

# 2. 構建前端鏡像（運行時透過 /api/auth/config 自發現 local / sso）
docker build -t $frontendImage ./frontend
Assert-NativeSuccess "build frontend"

# 3. 可選推送：設定 PUSH=1 才推送，便於讓其他主機拉取同一製品
if ($env:PUSH -eq '1') {
    Write-Host "Pushing images to $env:REGISTRY ..."
    docker push $backendImage
    Assert-NativeSuccess "push backend"
    docker push $frontendImage
    Assert-NativeSuccess "push frontend"
}

Write-Host "=== Build complete ==="
Write-Host "部署範例："
Write-Host "  UAT : IMAGE_TAG=$env:IMAGE_TAG docker compose up -d"
Write-Host "  PROD: IMAGE_TAG=$env:IMAGE_TAG docker compose -f docker-compose.prod.yml up -d"
