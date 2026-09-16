# Authentication 踩坑

本文記錄 Lab 04 Hot Reload 與 Lab 05 Authentication 開發時實際遇到的問題、原因、解法與避免方式。

## 1. 未登入請求回傳 403，而不是預期的 401

### 現象

呼叫受保護 API：

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/tasks' -Method Get
```

Spring Security 可能回傳：

```text
403 Forbidden
```

### 原因

SecurityConfig 只有設定：

```java
.anyRequest().authenticated()
```

沒有明確設定 authentication entry point 時，Spring Security 對 anonymous request 的預設處理可能是 403。

### 解法

將未認證與已認證但權限不足分開設定：

```java
.exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
        .accessDeniedHandler((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
```

預期結果：

```text
沒有 token -> 401 Unauthorized
有 token 但角色不足 -> 403 Forbidden
```

## 2. JWT filter 靜默吞掉解析錯誤

### 問題

`JwtAuthenticationFilter` 捕捉 `RuntimeException` 後只清除 SecurityContext：

```java
} catch (RuntimeException ignored) {
    SecurityContextHolder.clearContext();
}
```

過期 token、錯誤 token 或 JWT secret 不一致都會變成未登入狀態，最後可能看到 401 或 403，卻不知道真正原因。

### 排查重點

- 確認瀏覽器 localStorage 的 token 是否為舊 token。
- 確認產生 token 與解析 token 使用相同的 `JWT_SECRET`。
- 確認 header 格式是 `Authorization: Bearer <token>`。
- 查看 backend logs，而不是只看瀏覽器 console。

## 3. tasks entity 與 schema 欄位不一致

### 問題

`Task.java` 使用：

```java
private String priority;
private Long assigneeId;
```

MyBatis-Plus 會嘗試查詢：

```text
priority
assignee_id
```

但舊版 `schema.sql` 沒有這兩個欄位，查詢會失敗：

```text
ERROR: column "priority" does not exist
```

### 解法

在 `tasks` table 補上與 entity 對應的欄位：

```sql
priority VARCHAR(20) DEFAULT 'MEDIUM',
assignee_id INTEGER REFERENCES users(id),
```

保留既有的 `created_by` 欄位，避免破壞既有資料模型。

### 重要

PostgreSQL init script 只會在空 volume 第一次啟動時執行。修改 schema 後，開發環境需要：

```powershell
docker compose down -v
docker compose up -d
```

`down -v` 會刪除開發資料，只能在確認資料不需要保留時使用。

## 4. 前端 import 被當成 `/api/index.js` 請求

### 現象

瀏覽器出現：

```text
GET http://localhost:5173/api/index.js 403 (Forbidden)
```

### 原因

API client 放在 Vite 專案根目錄外的模組路徑，開發伺服器沒有把它當成正常 source module 打包；同時 Vite 將 `/api` proxy 到 backend。

### 解法

將 API client 放在：

```text
frontend/src/api/index.js
```

在 Vue component 使用：

```js
import api from '../api/index.js'
```

這樣 `api/index.js` 會被 Vite 打包，不會再被瀏覽器當成 HTTP 資源請求。

## 5. 登入 API 不應帶入舊 JWT

API request interceptor 應排除登入 endpoint：

```js
const isLoginRequest = config.url?.endsWith('/auth/login')
if (token && !isLoginRequest) {
  config.headers.Authorization = `Bearer ${token}`
}
```

登入失敗或收到 401/403 時，應清除舊認證資料：

```js
localStorage.removeItem('token')
localStorage.removeItem('role')
```

## 6. 登入回應欄位讀錯

目前 backend 回應格式為：

```json
{
  "token": "<jwt>",
  "bearer": "Bearer",
  "user": {
    "username": "user",
    "role": "USER"
  }
}
```

因此 frontend 應使用：

```js
localStorage.setItem('token', data.token)
localStorage.setItem('role', data.user.role)
```

不能使用 `data.role`，否則 role 會是 `undefined`。

## 7. Authentication 驗證清單

- [ ] 正確帳密登入回傳 200
- [ ] 登入回應包含 token
- [ ] 沒有 token 存取受保護 API 回傳 401
- [ ] 有效 USER token 可以讀取一般任務 API
- [ ] 權限不足的操作回傳 403
- [ ] 修改 schema 後已確認是否需要重建 volume
- [ ] JWT secret 在產生與解析時一致
