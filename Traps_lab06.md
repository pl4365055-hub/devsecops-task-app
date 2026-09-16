# Lab 06 - Authorisation 踩坑

本文記錄角色授權開發時遇到的問題、原因、解法與驗證方式。

## 1. 只在前端隱藏選單，不等於 API 安全

### 問題

前端可以根據 role 隱藏使用者列表：

```vue
<el-menu-item v-if="role === 'ADMIN'" index="/users">
  用戶列表
</el-menu-item>
```

但使用者仍然可以直接呼叫：

```text
GET /api/users
```

或使用 browser developer tools、curl、Postman 繞過畫面。

### 解法

後端 SecurityConfig 必須限制 API：

```java
.requestMatchers("/api/users/**").hasRole("ADMIN")
```

前端 route guard 只改善使用者體驗，不能取代 backend authorization。

## 2. `/api/users` 只限制 DELETE，不會自動限制 GET

### 問題

如果只有 controller method 或特定 DELETE endpoint 有 ADMIN 限制，`GET /api/users` 仍可能讓一般 USER 讀取所有使用者。

### 解法

使用 path-level authorization 保護整個 resource：

```java
.requestMatchers("/api/users/**").hasRole("ADMIN")
```

這會同時保護：

```text
GET    /api/users
DELETE /api/users/{id}
```

## 3. `hasRole` 與 JWT role 名稱不一致

### 問題

JWT 內的 claim 是：

```json
{
  "role": "ADMIN"
}
```

但 Spring Security 的 `hasRole("ADMIN")` 會尋找：

```text
ROLE_ADMIN
```

### 解法

在 JwtAuthenticationFilter 中補上 `ROLE_` prefix：

```java
String authority = role.startsWith("ROLE_")
        ? role
        : "ROLE_" + role;
```

結果：

```text
ADMIN -> ROLE_ADMIN
USER  -> ROLE_USER
```

## 4. 把 401 和 403 混在一起

| 狀態碼 | 意義 | 常見原因 |
| --- | --- | --- |
| `401` | 未認證 | 沒有 token、token 過期、JWT secret 不一致 |
| `403` | 已認證但無權限 | USER 存取 ADMIN-only API |

SecurityConfig 應明確設定：

```java
.exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
        .accessDeniedHandler((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
```

如果 USER token 存取 `/api/users` 時得到 401，而不是 403，應先檢查 JWT filter 是否成功建立 authentication，不能直接當成角色不足。

## 5. Logout 只清除畫面狀態，路由仍可直接進入

### 問題

登出只做：

```js
router.push('/login')
```

使用者仍可直接輸入 `/tasks`，因為前端 route 沒有驗證 token。

### 解法

登出時清除認證資訊：

```js
localStorage.removeItem('token')
localStorage.removeItem('username')
localStorage.removeItem('role')
```

並在 router 針對受保護 route 使用 guard：

```js
if (to.meta.requiresAuth && !localStorage.getItem('token')) {
  return '/login'
}
```

這是 UX 層保護；後端仍必須使用 Spring Security 保護 API。

## 6. 403 response interceptor 不應把有效使用者登出

### 問題

如果 Axios 收到 403 就清除 token，USER 存取 ADMIN-only API 時會被誤登出。

### 解法

只有 401 才清除登入狀態並導向 login：

```js
if (error.response?.status === 401) {
  localStorage.removeItem('token')
  localStorage.removeItem('username')
  localStorage.removeItem('role')
  window.location.href = '/login'
}
```

403 應留在目前頁面顯示權限不足訊息。

## 7. 授權檢查清單

- [ ] `/api/auth/login` 公開
- [ ] `/api/tasks/**` 需要有效 JWT
- [ ] `/api/users/**` 只允許 ADMIN
- [ ] USER 無法透過 curl 繞過前端存取 users API
- [ ] JWT 的 `ADMIN` 已轉成 `ROLE_ADMIN`
- [ ] 未登入回 401
- [ ] 已登入但角色不足回 403
- [ ] logout 後直接進入 `/tasks` 會回到 login
- [ ] 403 不會清除有效登入資料
