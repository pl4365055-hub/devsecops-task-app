# DevSecOps Task App 踩坑紀錄

本文記錄目前開發過程中實際遇到的問題、原因、解法與避免方式。

## 1. Windows 指令與工作目錄

### 問題

執行：

```powershell
mkdir frontend backend docs
```

如果目錄已經存在，PowerShell 可能回傳錯誤或非 0 exit code。

### 解法

先確認目錄：

```powershell
Get-ChildItem -Force
```

建立目錄時使用 `-Force`：

```powershell
New-Item -ItemType Directory -Force frontend, backend, docs
```

### 避免方式

執行命令前先確認目前位置：

```powershell
Get-Location
```

進入指定專案時使用完整路徑：

```powershell
Set-Location '~\projects\docker-handson\devsecops-task-app'
```

## 2. Vite PostCSS 設定編碼錯誤

### 錯誤

```text
[plugin:vite:css] Failed to load PostCSS config
Unexpected token '�'
```

### 原因

Vite 讀取到格式錯誤或編碼異常的 PostCSS JSON 設定檔。`��{` 通常表示檔案可能使用 UTF-16、錯誤 BOM 或其他非預期編碼。

### 排查

```powershell
Get-ChildItem -Force -Recurse -File |
  Where-Object { $_.Name -match 'postcss|\.postcssrc' }
```

### 解法

如果專案沒有使用 PostCSS，可以建立有效的空設定：

```js
// frontend/postcss.config.cjs
module.exports = {}
```

也要確認 Vite 向上層目錄搜尋時，沒有讀到其他錯誤的 PostCSS 設定。

## 3. Vue Router 指向不存在的頁面

### 問題

Router 已經引用：

```js
import Login from '../views/Login.vue'
import Dashboard from '../views/Dashboard.vue'
import TaskList from '../views/TaskList.vue'
```

但 `src/views/` 目錄或頁面檔案不存在，會造成 Vite 無法解析模組。

### 解法

建立對應檔案：

```text
frontend/src/views/Login.vue
frontend/src/views/Dashboard.vue
frontend/src/views/TaskList.vue
```

並確認檔名大小寫完全一致。

## 4. App.vue 仍顯示預設 Vite 畫面

### 問題

`App.vue` 仍然使用：

```vue
<HelloWorld />
```

導致新增的路由頁面不會顯示。

### 解法

改為：

```vue
<template>
  <router-view />
</template>
```

## 5. Element Plus 已安裝但元件無法使用

### 原因

只安裝 `element-plus` 不代表 Vue 已經註冊元件。

### 解法

在 `main.js` 中加入：

```js
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'

createApp(App).use(router).use(ElementPlus).mount('#app')
```

如果使用 Element Plus 圖示，也要安裝或確認對應的 icons package，並在元件中明確匯入圖示。

## 6. Backend 缺少基本 Java 專案骨架

### 問題

原始 backend 只有部分 Java 檔案，曾缺少：

- Spring Boot 啟動類別
- `Task` entity
- `UserMapper`
- Service 和 Controller 的 package/import
- Entity getter/setter

### 錯誤型態

可能出現：

```text
cannot find symbol
class, interface, enum, or record expected
```

### 解法

補齊基本結構：

```text
src/main/java/com/devsecops/taskapp/
├─ TaskAppApplication.java
├─ controller/
├─ dto/
├─ entity/
├─ mapper/
├─ security/
└─ service/
```

每個 Java 檔案都要有正確的 `package`、import 和類別定義。

## 7. Java 版本太舊，系統沒有 Maven

### 問題

系統原本只有：

```text
Java 8
```

而 Spring Boot 3.2 需要 Java 17；同時系統也沒有 `mvn` 命令。

### 解法

在 `backend/.tools/` 安裝專案專用工具：

```text
backend/.tools/jdk/
backend/.tools/maven/
```

使用腳本啟動：

```powershell
cd backend
.\mvn-local.ps1 spring-boot:run
```

確認版本：

```powershell
.\mvn-local.ps1 -version
```

必須看到 Java 17，而不是系統 Java 8。

### 注意

`.tools/` 和 `target/` 不應提交到 Git，因此已加入 `.gitignore`。

## 8. TaskMapper 重複 package 宣告

### 錯誤

```text
class, interface, enum, or record expected
```

### 原因

Java 檔案開頭重複出現：

```java
package com.devsecops.taskapp.mapper;
package com.devsecops.taskapp.mapper;
```

### 解法

每個 Java 檔案只能有一個 `package` 宣告。

## 9. MyBatis-Spring 與 Spring Boot 3.2 不相容

### 錯誤

```text
Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

### 原因

舊版 `mybatis-spring` 2.x 與 Spring Framework 6.1 不相容。

### 解法

使用相容版本：

```xml
<mybatis-plus.version>3.5.7</mybatis-plus.version>
<mybatis-spring.version>3.0.3</mybatis-spring.version>
```

目前 `pom.xml` 使用：

```xml
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-boot-starter</artifactId>
    <version>3.5.7</version>
</dependency>

<dependency>
    <groupId>org.mybatis</groupId>
    <artifactId>mybatis-spring</artifactId>
    <version>3.0.3</version>
</dependency>
```

檢查實際解析版本：

```powershell
.\mvn-local.ps1 dependency:tree '-Dincludes=org.mybatis:mybatis-spring'
```

## 10. Docker 內的 postgres 主機名無法在 Windows 主機解析

### 錯誤

```text
UnknownHostException: postgres
```

### 原因

`postgres` 是 Docker Compose 網路中的服務名稱。

- Backend 在 Docker 容器內執行時，可以使用 `postgres`
- Backend 直接在 Windows 主機執行時，應使用 `localhost`

### 解法

`application.yml` 使用環境變數：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/taskdb
```

本機執行：

```powershell
$env:DB_HOST = 'localhost'
```

若 backend 也放進 Docker Compose，才設定：

```text
DB_HOST=postgres
```

## 11. Docker Desktop 有安裝但 PostgreSQL 容器沒有啟動

### 問題

Backend 可以啟動，但呼叫 login API 時連不到資料庫。

### 檢查

```powershell
docker ps
```

如果沒有 `devsecops-task-postgres`，啟動：

```powershell
docker compose up -d postgres
```

確認容器：

```powershell
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}'
```

## 12. Schema 檔案放錯位置或掛載錯誤

### 問題

目前專案使用：

```text
backend/src/main/resources/schema.sql
```

曾經錯誤掛載成：

```text
backend/resources/schema.sql
```

### 解法

`docker-compose.yml` 必須使用：

```yaml
volumes:
  - ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql:ro
```

目前初始化來源是：

```text
backend/src/main/resources/schema.sql
```

## 13. PostgreSQL 初始化腳本只會執行一次

### 問題

修改 `schema.sql` 後重新執行 `docker compose up`，資料庫內容沒有變化。

### 原因

PostgreSQL 官方映像檔只會在資料目錄為空時執行 `/docker-entrypoint-initdb.d/` 內的腳本。舊 volume 存在時不會重新執行。

### 開發環境解法

```powershell
docker compose down -v
docker compose up -d postgres
```

注意：`down -v` 會刪除資料庫 volume，正式環境不可隨意使用。

## 14. BCrypt 測試密碼 Hash 不正確

### 問題

使用：

```text
admin / password
```

呼叫 login API 返回：

```text
401 Unauthorized
```

### 原因

`schema.sql` 中原本的 BCrypt hash 與 `password` 不匹配，或舊 volume 仍保存舊 hash。

### 解法

- 使用正確的 BCrypt hash
- 重新初始化資料庫 volume
- 確認目前資料庫內的使用者資料

測試流程：

```powershell
docker compose down -v
docker compose up -d postgres
```

然後：

```powershell
$body = @{
  username = 'admin'
  password = 'password'
} | ConvertTo-Json

Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/auth/login' `
  -Method POST `
  -ContentType 'application/json' `
  -Body $body
```

## 15. 舊 Java 程序仍佔用 8080

### 問題

修改後重新啟動 backend，但 API 行為仍像舊版本，甚至返回 `403`。

### 檢查

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

### 解法

停止佔用程序：

```powershell
$connection = Get-NetTCPConnection -LocalPort 8080 -State Listen
Stop-Process -Id $connection.OwningProcess -Force
```

再重新啟動：

```powershell
cd backend
.\mvn-local.ps1 spring-boot:run
```

## 16. API 登入請求失敗的分層判斷

### `403 Forbidden`

通常先檢查：

- 是否打到舊的 backend 進程
- `/api/auth/login` 是否設定 `permitAll()`
- 是否有其他 Security 設定攔截登入

### `401 Unauthorized`

通常表示請求已到達登入邏輯，但：

- 使用者不存在
- 密碼錯誤
- BCrypt hash 不匹配

### `UnknownHostException: postgres`

表示不是帳密問題，而是 backend 找不到 PostgreSQL 主機名。

### `Connection refused`

表示主機名稱可能正確，但 PostgreSQL 容器未啟動、port 未開放或服務尚未 ready。

## 17. PowerShell 多行 API 指令

PowerShell 使用反引號 `` ` `` 續行：

```powershell
Invoke-RestMethod `
  -Uri 'http://localhost:8080/api/auth/login' `
  -Method POST `
  -ContentType 'application/json' `
  -Body $body
```

常見問題：

- 反引號後面不能有空白
- 必須在同一個 PowerShell 工作階段先建立 `$body`
- 必須確認目前 backend 已啟動
- 必須確認 PostgreSQL 容器已啟動

也可以寫成單行，降低續行錯誤：

```powershell
$body = @{ username = 'admin'; password = 'password' } | ConvertTo-Json; Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' -Method POST -ContentType 'application/json' -Body $body
```

## 18. 最後驗證清單

### 啟動順序

```powershell
# 專案根目錄
docker compose up -d postgres
```

```powershell
# backend 目錄
cd backend
.\mvn-local.ps1 spring-boot:run
```

```powershell
# frontend 目錄
cd frontend
npm install
npm run dev
```

### 驗證項目

- [ ] `docker ps` 看得到 PostgreSQL 容器
- [ ] PostgreSQL 對外開放 `5432`
- [ ] backend 使用 Java 17 啟動
- [ ] backend 監聽 `8080`
- [ ] frontend 監聽 `5173`

## 19. Compose 掛載不存在的檔案會變成目錄

### 錯誤

```text
could not read from input file: Is a directory
```

### 原因

如果主機端的 schema 路徑不存在，Docker Compose 可能會自動建立同名目錄，容器內的 PostgreSQL 就無法把它當成 SQL 檔案讀取。

### 排查

```powershell
Test-Path .\backend\src\main\resources\schema.sql -PathType Leaf
```

結果必須是：

```text
True
```

### 解法

確認 Compose 使用正確的檔案路徑：

```yaml
- ./backend/src/main/resources/schema.sql:/docker-entrypoint-initdb.d/schema.sql:ro
```

修正後重建資料庫：

```powershell
docker compose down -v
docker compose up -d postgres
```

## 20. Compose 內 Backend 使用錯誤的資料庫 Host

### 問題

Backend 在 Docker container 內執行，卻使用 `localhost` 連接 PostgreSQL。

### 原因

在 Backend container 內，`localhost` 指的是 Backend 自己，不是 PostgreSQL container。

### 解法

Compose 內的 backend 必須設定：

```yaml
environment:
  DB_HOST: postgres
```

`postgres` 是 Compose service name，會在同一個 Compose network 內解析到資料庫 container。

## 21. Container 狀態是 Created 但沒有啟動

### 排查

```powershell
docker compose ps -a
docker compose logs postgres
```

如果 PostgreSQL 初始化失敗，backend 會因為：

```yaml
depends_on:
  postgres:
    condition: service_healthy
```

而等待或無法啟動。先修正 PostgreSQL 的錯誤，再重新執行：

```powershell
docker compose down -v
docker compose up --build
```

## 22. Docker Compose `version is obsolete` 警告

### 警告

```text
the attribute `version` is obsolete, it will be ignored
```

### 說明

Compose v2 會忽略：

```yaml
version: '3.8'
```

這通常只是警告，不會直接造成啟動失敗。可以移除 `version` 欄位，改用目前的 Compose Specification。

## 23. JWT Secret 太短導致 Backend 無法啟動

### 錯誤

```text
WeakKeyException: The specified key byte array is 112 bits
which is not secure enough for any JWT HMAC-SHA algorithm
```

### 原因

Compose 使用了過短的預設值：

```yaml
JWT_SECRET: ${JWT_SECRET:-dev-secret-key}
```

JJWT 的 HMAC signing key 至少需要 256 bits，也就是至少 32 bytes。

### 解法

使用至少 32 bytes 的開發用 secret：

```yaml
JWT_SECRET: ${JWT_SECRET:-dev-secret-key-change-in-production-32bytes}
```

正式環境應透過環境變數或 Secret 管理工具提供隨機長字串，不要使用文件中的開發用值。
