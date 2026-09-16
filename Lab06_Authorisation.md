# Lab 06 - Authorisation

## 1. 學習目標

- 理解 authentication 與 authorisation 的差異
- 使用 JWT role 建立 Spring Security authority
- 限制 ADMIN-only API
- 了解前端 route guard 與後端 API security 的責任邊界
- 使用 PowerShell 驗證 401、403 與 200

## 2. Authentication 與 Authorisation

```text
Authentication
  誰是你？
  username + password -> JWT

Authorisation
  你可以做什麼？
  JWT role -> Spring Security authority -> API permission
```

本專案的角色：

| Role | Authority | 可存取範圍 |
| --- | --- | --- |
| `USER` | `ROLE_USER` | 任務 API |
| `ADMIN` | `ROLE_ADMIN` | 任務 API、使用者 API |

## 3. 後端 API 規則

目前 SecurityConfig：

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login").permitAll()
        .requestMatchers("/api/users/**").hasRole("ADMIN")
        .requestMatchers("/api/tasks/**").authenticated()
        .anyRequest().authenticated())
```

權限矩陣：

| Endpoint | 未登入 | USER | ADMIN |
| --- | --- | --- | --- |
| `POST /api/auth/login` | 200 或 401 | 200 或 401 | 200 或 401 |
| `GET /api/tasks` | 401 | 200 | 200 |
| `POST /api/tasks` | 401 | 200 | 200 |
| `PUT /api/tasks/{id}` | 401 | 200 | 200 |
| `DELETE /api/tasks/{id}` | 401 | 200 | 200 |
| `GET /api/users` | 401 | 403 | 200 |
| `DELETE /api/users/{id}` | 401 | 403 | 204 或 404 |

`/api/users/**` 的 ADMIN-only 規則是後端真正的安全邊界。

## 4. JWT role 到 authority

登入時 JWT claim 內容類似：

```json
{
  "sub": "admin",
  "role": "ADMIN",
  "iat": 1789524959,
  "exp": 1789611359
}
```

`JwtAuthenticationFilter` 讀取 role 後補上 prefix：

```java
String authority = role.startsWith("ROLE_")
        ? role
        : "ROLE_" + role;
```

因此 Spring Security 取得：

```text
ADMIN -> ROLE_ADMIN
USER  -> ROLE_USER
```

這樣：

```java
.hasRole("ADMIN")
```

才會正確匹配 `ROLE_ADMIN`。

## 5. Frontend route guard

前端 router 可限制頁面導航：

```js
const isAuthenticated = Boolean(localStorage.getItem('token'))

if (to.meta.requiresAuth && !isAuthenticated) {
  return '/login'
}

if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
  return '/dashboard'
}
```

`/users` route：

```js
{
  path: '/users',
  component: Users,
  meta: { requiresAuth: true, requiresAdmin: true }
}
```

Route guard 只保護前端畫面。不能取代 backend authorization，因為 API 可以直接由 curl、Postman 或其他 client 呼叫。

## 6. PowerShell 驗證

### 6.1 取得不同角色的 token

```powershell
function Get-Token($username) {
    $body = @{
        username = $username
        password = 'password'
    } | ConvertTo-Json

    return (Invoke-RestMethod `
        -Uri 'http://localhost:8080/api/auth/login' `
        -Method Post `
        -ContentType 'application/json' `
        -Body $body).token
}

$userToken = Get-Token 'user'
$adminToken = Get-Token 'admin'
```

### 6.2 USER 讀取任務

```powershell
$userHeaders = @{ Authorization = "Bearer $userToken" }
$tasks = Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/tasks' `
    -Method Get `
    -Headers $userHeaders

Write-Host "USER tasks: 200, count=$($tasks.Count)"
```

預期：

```text
USER tasks: 200
```

### 6.3 USER 嘗試讀取使用者列表

```powershell
try {
    Invoke-RestMethod `
        -Uri 'http://localhost:8080/api/users' `
        -Method Get `
        -Headers $userHeaders
    throw 'USER 不應該可以讀取 users API'
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -ne 403) {
        throw "預期 403，實際為 $status"
    }
    Write-Host 'USER users: 403'
}
```

### 6.4 ADMIN 讀取使用者列表

```powershell
$adminHeaders = @{ Authorization = "Bearer $adminToken" }
$users = Invoke-RestMethod `
    -Uri 'http://localhost:8080/api/users' `
    -Method Get `
    -Headers $adminHeaders

Write-Host "ADMIN users: 200, count=$($users.Count)"
```

預期：

```text
ADMIN users: 200
```

### 6.5 未登入存取 API

```powershell
try {
    Invoke-RestMethod -Uri 'http://localhost:8080/api/users' -Method Get
    throw '未登入不應該成功'
} catch {
    $status = [int]$_.Exception.Response.StatusCode
    if ($status -ne 401) {
        throw "預期 401，實際為 $status"
    }
    Write-Host 'Anonymous users: 401'
}
```

## 7. Logout 後的前端行為

登出時應清除：

```js
localStorage.removeItem('token')
localStorage.removeItem('username')
localStorage.removeItem('role')
```

之後直接開啟：

```text
http://localhost:5173/tasks
```

router guard 應導向：

```text
http://localhost:5173/login
```

即使前端 route guard 被繞過，backend 仍會因為沒有 Bearer token 回傳 `401`。

## 8. 驗證流程

```text
1. Login 取得 JWT
2. JWT filter 驗證 token
3. JWT role 轉成 ROLE_USER 或 ROLE_ADMIN
4. SecurityConfig 判斷 endpoint 規則
5. 通過後才進入 Controller
```

- `401`：沒有有效身份
- `403`：身份有效但權限不足
- `200`：身份與權限都符合

## 9. 檢查清單

- [ ] USER 可以讀取 `/api/tasks`
- [ ] ADMIN 可以讀取 `/api/tasks`
- [ ] USER 存取 `/api/users` 得到 403
- [ ] ADMIN 存取 `/api/users` 得到 200
- [ ] 未登入存取受保護 API 得到 401
- [ ] 前端 `/users` 只對 ADMIN 顯示
- [ ] logout 後 `/tasks` 導向 `/login`
- [ ] 前端隱藏選單不被視為後端安全措施
