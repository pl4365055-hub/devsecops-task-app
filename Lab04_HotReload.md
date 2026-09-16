# Lab 04 - Hot Reload

## 1. 學習目標

- 理解 Docker Compose volume mount 如何讓 container 使用主機原始碼
- 使用 Spring Boot DevTools 重新載入 backend
- 使用 Vite dev server 重新載入 frontend
- 判斷何時只需要 reload，何時必須重新建構 image 或重建 volume

## 2. 開發環境服務

本 Lab 使用 `docker-compose.dev.yml`：

| Service | Container 內工作目錄 | 開發方式 |
| --- | --- | --- |
| `postgres` | PostgreSQL data directory | 使用 named volume |
| `backend` | `/app` | 掛載 `./backend:/app`，執行 `mvn spring-boot:run` |
| `frontend` | `/app` | 掛載 `./frontend:/app`，執行 Vite dev server |

主要設定：

```yaml
backend:
  working_dir: /app
  command: mvn spring-boot:run
  volumes:
    - ./backend:/app
    - maven-repo:/root/.m2

frontend:
  working_dir: /app
  command: sh -c "npm install && npm run dev"
  volumes:
    - ./frontend:/app
    - /app/node_modules
  environment:
    - CHOKIDAR_USEPOLLING=true
```

## 3. 啟動開發環境

在 repository 根目錄執行：

```powershell
docker compose --file .\docker-compose.dev.yml up
```

背景啟動：

```powershell
docker compose --file .\docker-compose.dev.yml up -d
```

查看狀態：

```powershell
docker compose --file .\docker-compose.dev.yml ps
```

預期：

```text
postgres   healthy
backend    running
frontend   running
```

服務位址：

```text
Frontend: http://localhost:5173
Backend:  http://localhost:8080
```

## 4. Frontend Hot Reload

修改以下檔案並儲存：

```text
frontend/src/views/Login.vue
frontend/src/views/Dashboard.vue
frontend/src/style.css
```

Vite 會重新編譯受影響的模組，瀏覽器通常會透過 HMR 更新畫面。

如果 container 內偵測不到 Windows 主機的檔案變更，使用：

```yaml
environment:
  - CHOKIDAR_USEPOLLING=true
```

檢查 frontend logs：

```powershell
docker compose --file .\docker-compose.dev.yml logs -f frontend
```

## 5. Backend Hot Reload

修改 Java source 或 resources 後，Spring Boot DevTools 會嘗試重新啟動 application context。

查看 logs：

```powershell
docker compose --file .\docker-compose.dev.yml logs -f backend
```

如果只修改 Java 程式碼但沒有重新編譯，DevTools 可能不會立即偵測到變更。此時可手動重新建立 container：

```powershell
docker compose --file .\docker-compose.dev.yml restart backend
```

若 Maven 依賴或 compose command 改變，使用：

```powershell
docker compose --file .\docker-compose.dev.yml up --force-recreate backend
```

## 6. Hot Reload、Build、Volume 的區別

### 只修改 frontend source

通常不需要 rebuild image：

```powershell
docker compose --file .\docker-compose.dev.yml logs -f frontend
```

### 只修改 backend Java source

先觀察 DevTools 是否重新啟動；沒有反應時：

```powershell
docker compose --file .\docker-compose.dev.yml restart backend
```

### 修改 Dockerfile 或 package 依賴

需要重新建構或重新安裝依賴：

```powershell
docker compose --file .\docker-compose.dev.yml up --build
```

### 修改 `schema.sql`

PostgreSQL init script 只會在空 volume 執行。開發資料可以刪除時：

```powershell
docker compose --file .\docker-compose.dev.yml down -v
docker compose --file .\docker-compose.dev.yml up -d
```

不要把 `down -v` 當成一般 reload 指令，因為它會刪除資料庫資料。

## 7. 常見問題

### `GET /api/index.js 403`

API client 被瀏覽器當成 `/api` 資源請求。確認 API client 在 `frontend/src/api/index.js`，並使用相對 source import。

### 修改檔案後畫面沒有變化

依序檢查：

```powershell
docker compose --file .\docker-compose.dev.yml ps
docker compose --file .\docker-compose.dev.yml logs --tail=100 frontend
```

再確認瀏覽器沒有快取舊資源，執行 hard refresh。

### Backend 顯示 `UnknownHostException: postgres`

只有 container 內的 backend 可以使用 `postgres` 作為 host。若 backend 在 Windows 主機執行，使用：

```powershell
$env:DB_HOST = 'localhost'
```

## 8. 檢查清單

- [ ] backend 掛載 `./backend:/app`
- [ ] frontend 掛載 `./frontend:/app`
- [ ] frontend 啟用 `CHOKIDAR_USEPOLLING`
- [ ] PostgreSQL service 為 healthy
- [ ] 修改 frontend 後 Vite 可更新畫面
- [ ] 修改 backend 後 DevTools 或 restart 可重新載入
- [ ] 知道 Dockerfile、依賴與 schema 變更需要不同處理方式
