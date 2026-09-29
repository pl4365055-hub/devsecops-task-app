# 🐙 Docker Compose 實戰：指令、.env、port 與部署生命週期

把 Lab 操作過程中發現、前面筆記沒覆蓋的東西集中在這裡：compose 指令的正確心智模型、port 對應、容器網路、prod compose 的變數機制，以及單獨 `docker run` 與 compose 的差異。

## 🔤 名詞中英對照

- Port mapping / publish：port 對應（`-p 主機:容器`）
- Bind mount：綁定掛載（`./backend:/app`，直接對應主機目錄）
- DNS-based service discovery：compose 網路內用服務名互相找到（如 `backend`）
- Image reference：映像名稱（registry/repo:tag 格式）
- Pull policy：拉取策略（compose 預設 `missing`：本機有就不拉）

---

## 一、核心心智模型：`-f` 每條指令都要帶

`-f docker-compose.prod.yml` 的效力**只存在該指令**，不會被記住：

```powershell
docker compose -f docker-compose.prod.yml pull   # ✅ 拉 prod 映像
docker compose up -d                              # ⚠️ 用回預設 docker-compose.yml！
docker compose down                               # ⚠️ 同樣是 docker-compose.yml
```

每一步都帶同一個 `-f`：

```powershell
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
docker compose -f docker-compose.prod.yml down
```

> `down` 靠「專案標籤」找容器，沒帶 `-f` 有時也停得到，但服務/網路/volume 定義會用錯檔案，不要依賴這個巧合。

## 二、`.env` 是「主機端變數替換」，每個指令都讀

- Compose 執行**任何**指令（pull、up、down）都自動讀專案目錄的 `.env`，與用哪個 `-f` 無關
- 替換發生在主機端，所以連單純 `pull` 都需要 `.env` 裡的 `REGISTRY`、`IMAGE_TAG`
- 優先級：**shell 環境變數 > `.env` 檔**；也可用 `--env-file` 指定其他檔案
- 變數沒有預設值又沒設定 → 展開成**空字串**，只跳 warning，不會自動補值

## 三、踩坑：prod compose 的 invalid reference format

`docker-compose.prod.yml` 沒有 `build`，只拉映像，且全部用變數、無預設值：

```yaml
image: ${REGISTRY}/task-app-backend:${IMAGE_TAG}
POSTGRES_DB: ${DB_NAME}
```

變數空白時映像名變成 `/task-app-backend:`（開頭斜線＋空 tag）→ `invalid reference format`。

解法：`.env` 補齊：

```env
DB_NAME=taskdb
REGISTRY=task-app
IMAGE_TAG=v1
```

**本機測試的眉角：**

- `task-app` 會被當成 **Docker Hub 帳號**，`pull` 一定往 Hub 拉 → 本機映像要先 tag 成預期名稱，並**跳過 pull 直接 up**（pull policy 是 `missing`，本機有就不拉）
- `DB_NAME` 空白有實質後果：Postgres 不建立 `taskdb`，backend 連不到預期資料庫
- `version: '3.8'` 的 obsolete 警告：新版 Compose 已忽略，刪掉該行即可
- 真實場景：`REGISTRY=ghcr.io/帳號`、`IMAGE_TAG=<git-sha>`，那時 pull 才有意義

## 四、部署生命週期：沒有 down

```
docker login → compose pull → compose up -d → 健康檢查驗證
```

- `down` 是停止並移除容器，**不是部署的一部分**
- `up -d` 會比對現有容器，只重建映像/設定有變的服務 → 零停機替換；先 down 反而造成中斷
- `docker login` 與 compose 獨立：登入憑證存在 `~/.docker/config.json`，之後 pull/up 自動帶上

## 五、Port 對應：compose 幫你做了 `-p`

- compose 的 `ports: - "80:80"` 等同 `docker run -p 80:80`
- 執行 `docker run -d --name frontend-test <image>` **沒加 `-p`** → 主機連不到，refuse to connect
- Port mapping **不能事後補加**，必須 `docker rm -f` 後重建：

```powershell
docker rm -f frontend-test
docker run -d --name frontend-test -p 8081:80 task-app/frontend:ab62291
```

## 六、5173 vs 80：dev 與 prod 是完全不同的 frontend

| 啟動檔案 | Frontend 容器 | 網址 |
|---|---|---|
| `docker-compose.dev.yml` | Vite dev server（hot reload） | **http://localhost:5173** |
| `docker-compose.yml` / `prod.yml` | nginx + 打包產物 | **http://localhost**（80） |

在 prod 開 `localhost:5173` 會 refuse to connect，因為 5173 沒有服務。Dev 的 Vite 第一次啟動需等 10–30 秒，用 logs 確認 `Local: http://localhost:5173/` 出現再重新整理。

## 七、容器網路：單獨 run 的容器找不到 `backend`

`frontend/nginx.conf`：

```nginx
location /api/ {
    proxy_pass http://backend:8080/api/;   # backend 是 compose 服務名
}
```

- `backend` 這個主機名只在 **compose 建立的網路**內可解析
- 單獨 `docker run` 的容器不在該網路 → 頁面能開，但 `/api/` 回 **502 Bad Gateway**
- 手動補救（讓 backend 以該別名加入同一網路）：

```powershell
docker network create app-net
docker network connect --alias backend app-net <backend容器名>
docker network connect app-net frontend-test
```

## 八、容器停止時：exec 進不去

- `docker exec` 只能對**運行中**的容器；停止中會報 `container ... is not running`
- Docker Desktop/daemon 重啟後，沒設 restart policy 的容器會停在 Exited
- 排查順序：

```powershell
docker ps -a                    # 看狀態
docker logs frontend-test       # 看退出原因
docker start frontend-test      # 啟動後再 exec
```

## 九、deploy.sh → deploy.ps1 的關鍵差異

- `set -e` → `$ErrorActionPreference = 'Stop'`，但**原生程式（docker、curl.exe）失敗只設 `$LASTEXITCODE`，不會停止腳本**，需自行檢查退出碼
- PowerShell 的 `curl` 是 `Invoke-WebRequest` 的別名 → 要用 `curl.exe`
- `sleep 10` → `Start-Sleep -Seconds 10`；環境變數 `$VAR` → `$env:VAR`

## ✅ 一句話總結

> Compose 的 `-f` 不記憶、`.env` 每個指令都讀；部署是 pull → up -d（沒有 down）；port 和服務間 DNS 都由 compose 網路提供，單獨 `docker run` 會同時失去這兩者。
