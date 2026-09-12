# Lab 03 - Docker Compose

## 1. 學習目標

- 理解 Image、Container、Service 和 Volume
- 使用 Docker Compose 管理 PostgreSQL、Backend 和 Frontend
- 理解 service name、port mapping、環境變數和 healthcheck
- 能夠排查 `docker compose up` 啟動失敗

## 2. 本專案服務

| Service | Image / Build | 用途 | 對外 Port |
| --- | --- | --- | --- |
| `postgres` | `postgres:16-alpine` | PostgreSQL 資料庫 | `5432` |
| `backend` | `./backend/Dockerfile` | Spring Boot API | `8080` |
| `frontend` | `./frontend/Dockerfile` | Vue 編譯結果與 Nginx | `80` |

服務關係：

```text
Browser :80
    ↓
Frontend / Nginx
    ↓ API
Backend :8080
    ↓ DB_HOST=postgres
PostgreSQL :5432
```

## 3. Compose 設定重點

### 3.1 PostgreSQL 環境變數

```yaml
POSTGRES_DB: taskdb
POSTGRES_USER: ${DB_USERNAME:-postgres}
POSTGRES_PASSWORD: ${DB_PASSWORD:-postgres}
```

`${DB_USERNAME:-postgres}` 的意思是：

```text
有設定 DB_USERNAME → 使用外部值
沒有設定 DB_USERNAME → 使用 postgres
```

### 3.2 Volume

```yaml
volumes:
  - postgres-data:/var/lib/postgresql/data
```

Named volume 用來保存 PostgreSQL 資料。刪除 container 後，資料仍然存在。

刪除資料庫 volume：

```powershell
docker compose down -v
```

注意：`down -v` 會刪除資料，只適合開發環境重新初始化。

### 3.3 Schema 掛載

目前 schema 位於：

```text
backend/src/main/resources/schema.sql
```

Compose 掛載：

```yaml
- ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql:ro
```

左側是主機檔案，右側是 PostgreSQL container 內的初始化檔案。`ro` 表示 read-only。

### 3.4 Healthcheck 與啟動順序

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U postgres"]
  interval: 5s
  timeout: 5s
  retries: 5
```

Backend 使用：

```yaml
depends_on:
  postgres:
    condition: service_healthy
```

這表示 PostgreSQL 通過 healthcheck 後，Backend 才會啟動。

### 3.5 Docker 內的資料庫 Host

Backend 在 Windows 主機執行時：

```text
DB_HOST=localhost
```

Backend 在 Compose container 內執行時：

```yaml
environment:
  DB_HOST: postgres
```

`postgres` 是 Compose service name，只能在 Compose network 內使用。

## 4. 常用指令

### 4.1 檢查設定

```powershell
docker compose config
```

這會解析 YAML 和環境變數。`version is obsolete` 通常只是警告，不是啟動失敗原因。

### 4.2 建構並啟動

前景執行並顯示 logs：

```powershell
docker compose up --build
```

背景執行：

```powershell
docker compose up --build -d
```

### 4.3 查看狀態與 Logs

```powershell
docker compose ps
docker compose ps -a
docker compose logs --tail=100 postgres
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
```

即時追蹤：

```powershell
docker compose logs -f backend
```

### 4.4 停止服務

保留資料：

```powershell
docker compose down
```

刪除資料並重新初始化：

```powershell
docker compose down -v
docker compose up --build
```

## 5. 啟動與驗證

### Step 1：驗證 Compose

```powershell
docker compose config
```

### Step 2：啟動全部服務

```powershell
docker compose up --build -d
```

### Step 3：查看狀態

```powershell
docker compose ps
```

預期：

```text
postgres   healthy
backend    running
frontend   running
```

### Step 4：確認 PostgreSQL 表格

```powershell
docker compose exec -T postgres psql -U postgres -d taskdb -c '\dt'
```

應該看到：

```text
tasks
users
```

### Step 5：測試登入 API

```powershell
$body = @{
  username = 'admin'
  password = 'password'
} | ConvertTo-Json

$response = Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/auth/login' `
  -Method POST `
  -ContentType 'application/json' `
  -Body $body

$response
```

### Step 6：開啟 Frontend

```text
http://localhost/login
```

登入帳號：

```text
admin / password
```

## 6. 常見錯誤

### `could not read from input file: Is a directory`

代表 schema 路徑不存在，Docker 將路徑建立成目錄。

確認：

```powershell
Test-Path .\backend\src\main\resources\schema.sql -PathType Leaf
```

結果必須是 `True`。修正後執行：

```powershell
docker compose down -v
docker compose up --build
```

### `UnknownHostException: postgres`

Backend 在 container 內卻沒有設定：

```yaml
DB_HOST: postgres
```

如果 Backend 直接在 Windows 執行，則使用：

```powershell
$env:DB_HOST = 'localhost'
```

### Backend 狀態是 `Created`

先檢查資料庫：

```powershell
docker compose ps -a
docker compose logs postgres
```

PostgreSQL 初始化失敗時，Backend 可能因為 `service_healthy` 條件而無法啟動。

### 修改 schema 後資料沒有更新

PostgreSQL 只會在空的資料目錄第一次啟動時執行初始化腳本。開發環境重新建立：

```powershell
docker compose down -v
docker compose up -d postgres
```

### `port is already allocated`

表示 80、8080 或 5432 已被其他程序使用。

```powershell
Get-NetTCPConnection -LocalPort 80,8080,5432 -ErrorAction SilentlyContinue
```

也可以修改對外 port：

```yaml
ports:
  - "8081:8080"
```

### 修改程式碼後容器沒有更新

程式碼已經被打包進 image 時，需要重新建構：

```powershell
docker compose up --build
```

## 7. 必知必會

```powershell
# 檢查設定
docker compose config

# 建構並啟動
docker compose up --build

# 背景啟動
docker compose up -d

# 查看所有 container
docker compose ps -a

# 查看 logs
docker compose logs --tail=100 backend

# 進入 PostgreSQL
docker compose exec postgres psql -U postgres -d taskdb

# 停止但保留資料
docker compose down

# 停止並刪除資料
docker compose down -v
```

## 8. Lab 03 檢查清單

- [ ] `docker compose config` 沒有 YAML 錯誤
- [ ] `backend/src/main/resources/schema.sql` 是檔案，不是目錄
- [ ] PostgreSQL 狀態為 `healthy`
- [ ] `users` 和 `tasks` 表格成功建立
- [ ] Backend 的 `DB_HOST` 為 `postgres`
- [ ] Backend container 成功啟動
- [ ] Frontend container 成功啟動
- [ ] `http://localhost/login` 可以開啟
- [ ] `admin/password` 可以取得 JWT
- [ ] 修改 schema 後知道何時需要 `down -v`
