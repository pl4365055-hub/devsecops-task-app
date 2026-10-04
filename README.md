# DevSecOps Task App

一個 Vue + Spring Boot + PostgreSQL 的任務管理應用，包含 JWT 登入、使用者管理和任務管理功能。

## 專案結構

```text
├─ backend/       Spring Boot API
├─ frontend/      Vue + Vite 前端
├─ docs/          專案文件
├─ docker-compose.yml
└─ README.md
```

## 技術與環境需求

- Docker Desktop
- Node.js 和 npm
- Java 17
- Maven 3.9.11
- PostgreSQL 16

不需要在系統安裝 Java/Maven，backend 已提供專案本地工具：

```text
backend/.tools/jdk/       Java 17
backend/.tools/maven/     Maven 3.9.11
backend/mvn-local.ps1     專案本地 Maven 啟動腳本
```

## Docker Compose 完整啟動

在專案根目錄執行：

```powershell
docker compose up --build
```

服務位址：

```text
Frontend:    http://localhost/login
Backend API: http://localhost:8080
PostgreSQL:  localhost:5432
```

背景啟動：

```powershell
docker compose up --build -d
docker compose ps
```

查看服務記錄：

```powershell
docker compose logs --tail=100 postgres
docker compose logs --tail=100 backend
docker compose logs --tail=100 frontend
```

停止服務但保留資料：

```powershell
docker compose down
```

停止服務並刪除資料庫 volume：

```powershell
docker compose down -v
```

`down -v` 會刪除 PostgreSQL 資料，只適合開發環境重新初始化。

## PostgreSQL

Compose 使用 PostgreSQL service：

```yaml
postgres:
  image: postgres:16-alpine
```

資料庫初始化腳本位於：

```text
backend/src/main/resources/schema.sql
```

此腳本會建立 `users` 和 `tasks` 資料表，並建立測試帳號：

```text
username: admin
password: password
role: ADMIN
```

PostgreSQL 只會在第一次建立空的 volume 時執行初始化腳本。修改 schema 後如需重新初始化：

```powershell
docker compose down -v
docker compose up -d postgres
```

## Backend

### Docker 模式

Compose 內的 Backend 使用：

```yaml
DB_HOST: postgres
```

`postgres` 是 Docker Compose network 內的 service name。

### 本機模式

```powershell
cd backend
.\mvn-local.ps1 spring-boot:run
```

本機執行時資料庫預設連線到 `localhost:5432`：

```powershell
$env:DB_HOST = "localhost"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
```

Backend 位址：

```text
http://localhost:8080
```

編譯打包：

```powershell
.\mvn-local.ps1 -q -DskipTests package
```

確認本地 Java 和 Maven：

```powershell
.\mvn-local.ps1 -version
```

### Backend API

登入：

```text
POST /api/auth/login
```

查詢使用者：

```text
GET /api/users
```

刪除使用者，僅限 ADMIN：

```text
DELETE /api/users/{id}
```

任務 API：

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}
```

除了登入介面以外，API 請求需要攜帶：

```text
Authorization: Bearer <JWT_TOKEN>
```

## Frontend

### 本機開發模式

```powershell
cd frontend
npm install
npm run dev
```

開發頁面：

```text
http://localhost:5173/login
```

主要頁面：

- `/login`：使用者名稱和密碼登入
- `/dashboard`：儀表板、側邊導覽和歡迎訊息
- `/tasks`：任務列表、新增、編輯和刪除

建置正式版本：

```powershell
npm run build
```

### Docker 模式

Frontend 會透過 Dockerfile 建置，再由 Nginx 提供服務：

```text
http://localhost/login
```

## 測試登入 API

```powershell
$body = @{
  username = "admin"
  password = "password"
} | ConvertTo-Json

$response = Invoke-RestMethod `
  -Uri "http://localhost:8080/api/auth/login" `
  -Method POST `
  -ContentType "application/json" `
  -Body $body

$token = $response.token
$response
```

查詢使用者：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/users" `
  -Headers @{ Authorization = "Bearer $token" }
```

刪除使用者：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/users/2" `
  -Method DELETE `
  -Headers @{ Authorization = "Bearer $token" }
```

## JWT Secret

JJWT HMAC signing key 至少需要 32 bytes。Compose 使用開發用預設值：

```yaml
JWT_SECRET: ${JWT_SECRET:-dev-secret-key-change-in-production-32bytes}
```

正式環境請使用環境變數或 Secret 管理工具提供隨機長字串：

```powershell
$env:JWT_SECRET = "請替換成至少 32 bytes 的正式密鑰"
```

## 本機開發啟動順序

如果 PostgreSQL 使用 Docker、Backend 和 Frontend 在 Windows 主機執行：

```powershell
# Terminal 1：專案根目錄
docker compose up -d postgres
```

```powershell
# Terminal 2：backend
cd backend
.\mvn-local.ps1 spring-boot:run
```

```powershell
# Terminal 3：frontend
cd frontend
npm run dev
```

然後開啟：

```text
http://localhost:5173/login
```

注意：

- Backend 在 Windows 主機執行時，`DB_HOST=localhost`
- Backend 在 Compose container 內執行時，`DB_HOST=postgres`
- 不要把這兩種設定混用

## 多環境認證（Build Once, Deploy Many）

同一個後端映像檔，認證方式完全由 `SPRING_PROFILES_ACTIVE` 決定；敏感值一律由環境變數注入，不打包進映像檔。

| Profile | 認證方式 | 憑證來源 | Token |
|---|---|---|---|
| `dev` | Mock 登入（不檢查密碼） | 記憶體假使用者（登入）；任務模組仍用 PostgreSQL | 應用自簽 JWT |
| `uat` | 帳號密碼登入 | PostgreSQL `users` 表（schema.sql 初始化） | 應用自簽 JWT |
| `prod` | Keycloak SSO，後端為 OAuth2 Resource Server | Keycloak 簽發、後端 JWK 驗章 | Keycloak JWT |
| `test` | 同 uat（整合測試用） | Testcontainers PostgreSQL | 應用自簽 JWT |

前端啟動時呼叫 `GET /api/auth/config` 自動發現認證模式：`local` 顯示帳號密碼表單，`sso` 跳轉 Keycloak 登入頁（Authorization Code + PKCE）。

### dev（Mock 登入）

```powershell
docker compose -f docker-compose.dev.yml up
```

此環境會一併啟動 PostgreSQL（任務模組需要），但**登入不檢查密碼、不查 users 表**。可用帳號（密碼任意）：

```text
admin / 任意密碼  -> ADMIN
user  / 任意密碼  -> USER
```

### uat（資料庫帳號密碼，預設 compose）

```powershell
docker compose up --build
```

帳號：`admin / password`、`user / password`。

### prod（Keycloak SSO）

先啟動 Keycloak（首次會自動匯入 `taskapp` realm 與測試使用者）：

```powershell
docker compose -f docker-compose.keycloak.yml up -d
```

再以 prod compose 啟動應用（需提供 `.env` 中的 `REGISTRY`、`IMAGE_TAG`、`DB_*`、`JWT_SECRET`）：

```powershell
docker compose -f docker-compose.prod.yml up -d
```

Keycloak 位址與帳號：

```text
Keycloak:    http://localhost:9080
管理控制台:  admin / admin
測試使用者:  admin / password、user / password
Realm 設定:  infra/keycloak/realm-export.json
```

Realm 檔案定義了 `taskapp` realm、public client `task-app`（啟用 PKCE）、`ADMIN`/`USER` realm roles 及兩個測試使用者。

## 相關文件

- [Lab01_frontend.md](Lab01_frontend.md)
- [Lab02_backend.md](Lab02_backend.md)
- [Lab03_docker-compose.md](Lab03_docker-compose.md)
- [Traps.md](Traps.md)
- [docs/auth-flow.md](docs/auth-flow.md)：多環境認證流程梳理（local / SSO、時序圖）
