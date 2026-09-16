# Lab 05 - Authentication

## 1. 學習目標

- 使用 Spring Security 保護 API
- 使用 JWT 完成 stateless authentication
- 理解 authentication 與 authorization 的差異
- 從 Vue frontend 登入並保存 token
- 使用 PowerShell 驗證 200、401 與 403

## 2. Authentication 流程

```text
Browser
  |
  | POST /api/auth/login
  | username + password
  v
AuthController
  |
  | 查詢 users、BCrypt 比對密碼
  v
JwtService
  |
  | 產生 JWT：subject + role + expiration
  v
Browser localStorage
  |
  | Authorization: Bearer <token>
  v
JwtAuthenticationFilter
  |
  | 驗證簽章、讀取 username 與 role
  v
Protected Controller
```

Authentication 是確認「你是誰」；authorization 是確認「你能做什麼」。

## 3. 後端 SecurityConfig

核心設定：

```java
.csrf(csrf -> csrf.disable())
.sessionManagement(session ->
        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login").permitAll()
        .anyRequest().authenticated())
.addFilterBefore(jwtAuthenticationFilter,
        UsernamePasswordAuthenticationFilter.class)
```

登入 endpoint 必須公開，其他 API 必須先通過 JWT authentication。

未認證與權限不足應分開處理：

```java
.exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
        .accessDeniedHandler((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
```

回應碼意義：

| 狀態碼 | 意義 |
| --- | --- |
| `200` | 驗證成功且允許執行 |
| `401` | 沒有有效 authentication |
| `403` | 已 authentication，但沒有足夠權限 |

## 4. JWT Authentication Filter

Filter 從 request header 讀取：

```text
Authorization: Bearer <token>
```

成功解析後建立 Spring Security authentication：

```java
String authority = role.startsWith("ROLE_")
        ? role
        : "ROLE_" + role;
```

這個轉換很重要，因為 `hasRole("ADMIN")` 預期的 authority 是 `ROLE_ADMIN`。

JWT secret 必須在產生與解析時一致。Compose 中使用：

```yaml
JWT_SECRET: ${JWT_SECRET:-dev-secret-key-change-in-production-32bytes}
```

正式環境應改用 secret manager 或安全的環境變數，不要使用文件中的開發用 secret。

## 5. 登入回應

目前登入 API：

```text
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

Request：

```json
{
  "username": "user",
  "password": "password"
}
```

Response 會包含：

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

Frontend 應保存：

```js
localStorage.setItem('token', data.token)
localStorage.setItem('role', data.user.role)
```

## 6. PowerShell API 測試

### 6.1 登入

```powershell
$body = @{
    username = 'user'
    password = 'password'
} | ConvertTo-Json

$login = Invoke-RestMethod -Uri 'http://localhost:8080/api/auth/login' `
    -Method Post `
    -ContentType 'application/json' `
    -Body $body

$login
$token = $login.token
```

預期：

```text
HTTP 200
$token 不為空
$login.user.role = USER
```

### 6.2 沒有 token

```powershell
try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/tasks' -Method Get
} catch {
    Write-Host "預期 401: $($_.Exception.Response.StatusCode.value__)"
}
```

### 6.3 帶 token

```powershell
$headers = @{ Authorization = "Bearer $token" }
$response = Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/tasks' `
    -Method Get `
    -Headers $headers

$response
Write-Host '預期 200: 任務列表請求成功'
```

### 6.4 完整測試腳本

```powershell
$body = @{
    username = 'user'
    password = 'password'
} | ConvertTo-Json

$login = Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/auth/login' `
    -Method Post `
    -ContentType 'application/json' `
    -Body $body

if ([string]::IsNullOrWhiteSpace($login.token)) {
    throw '登入回應沒有 token'
}

$token = $login.token
Write-Host "LOGIN_STATUS=200 ROLE=$($login.user.role)"

try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/tasks' -Method Get
    throw '未帶 token 不應成功'
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -ne 401) {
        throw "預期 401，實際為 $status"
    }
    Write-Host 'NO_TOKEN_STATUS=401'
}

$headers = @{ Authorization = "Bearer $token" }
$tasks = Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/tasks' `
    -Method Get `
    -Headers $headers

Write-Host 'TOKEN_STATUS=200'
$tasks
```

## 7. Frontend 登入注意事項

API client 放在：

```text
frontend/src/api/index.js
```

登入 request 不應附加舊 token：

```js
const isLoginRequest = config.url?.endsWith('/auth/login')
if (token && !isLoginRequest) {
  config.headers.Authorization = `Bearer ${token}`
}
```

登入前先驗證表單；登入成功後才保存 token 並導向 dashboard。收到 401 或 403 時清除 localStorage 的認證資料。

## 8. 檢查清單

- [ ] `/api/auth/login` 不需要 token
- [ ] 正確帳密回傳 200 和 JWT
- [ ] 錯誤帳密回傳 401
- [ ] 未帶 token 存取 `/api/tasks` 回傳 401
- [ ] 帶 USER token 讀取任務列表回傳 200
- [ ] ADMIN-only endpoint 對 USER 回傳 403
- [ ] JWT role 轉換為 `ROLE_USER` 或 `ROLE_ADMIN`
- [ ] token 過期或 secret 不一致時不會被視為已登入
- [ ] frontend 使用 `data.user.role` 保存 role
