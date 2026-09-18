# 個人理解 01 - Authentication 與 Authorisation

## 1. Security 的兩個部分

我理解 user security 主要分成兩個部分：

- **Authentication（身份驗證）**：確認「你是誰」
- **Authorisation（授權）**：確認「你可以做什麼」

Authentication 例如：

```text
username + password
        ↓
Backend 驗證身份
        ↓
簽發 JWT
```

Authorisation 例如：

```text
USER 只能使用一般任務 API
ADMIN 才能使用使用者管理 API
```

## 2. 前端的責任

Frontend 可以提供以下功能：

### Router guard

如果使用者沒有登入 token，將需要登入的頁面導向 `/login`：

```js
if (to.meta.requiresAuth && !localStorage.getItem('token')) {
  return '/login'
}
```

也可以限制只有 ADMIN 才能進入 `/users` 頁面：

```js
if (to.meta.requiresAdmin && localStorage.getItem('role') !== 'ADMIN') {
  return '/dashboard'
}
```

### 隱藏沒有權限的按鈕或選單

例如只有 ADMIN 顯示使用者列表：

```vue
<el-menu-item v-if="role === 'ADMIN'" index="/users">
  用戶列表
</el-menu-item>
```

### API client 加入 token

前端的 API client 會把登入後取得的 JWT 放入 request header：

```http
Authorization: Bearer <token>
```

這些前端功能主要是改善使用者體驗：

- 避免使用者進入不應看到的頁面
- 隱藏不適用的按鈕
- 自動把 token 加入 API request
- 在 token 失效時回到登入頁

但前端不能被視為真正的安全邊界，因為使用者可以：

- 直接輸入 URL
- 使用 browser developer tools
- 使用 curl 或 Postman
- 自己修改 JavaScript 或 localStorage

因此真正的 Authentication 與 Authorisation 必須由 backend 執行。

## 3. Backend 的安全流程

Backend 收到 API request 後，會先經過 Spring Security filter chain。

大致流程如下：

```text
HTTP Request
    ↓
Spring Security Filter Chain
    ↓
JwtAuthenticationFilter
    ↓
建立 Authentication
    ↓
SecurityContext
    ↓
Authorization rules / @PreAuthorize
    ↓
Controller
```

## 4. JwtAuthenticationFilter 的作用

`JwtAuthenticationFilter` 會從 request header 讀取：

```http
Authorization: Bearer <token>
```

接著執行以下步驟：

1. 取出 Bearer token
2. 使用 `JwtService` parse token
3. 驗證 JWT signature
4. 檢查 token 是否過期
5. 讀取 username 和 role
6. 建立 Spring Security 的 `Authentication`
7. 將 Authentication 放入 `SecurityContext`

概念上相當於：

```java
Claims claims = jwtService.parseToken(token);
String username = claims.getSubject();
String role = claims.get("role", String.class);

String authority = role.startsWith("ROLE_")
        ? role
        : "ROLE_" + role;

var authentication = new UsernamePasswordAuthenticationToken(
        username,
        null,
        List.of(new SimpleGrantedAuthority(authority))
);

SecurityContextHolder.getContext()
        .setAuthentication(authentication);
```

JWT 裡的 role 例如：

```text
ADMIN
```

會轉成 Spring Security authority：

```text
ROLE_ADMIN
```

同樣地：

```text
USER -> ROLE_USER
```

這個 prefix 很重要，因為：

```java
hasRole("ADMIN")
```

實際上會尋找：

```text
ROLE_ADMIN
```

## 5. SecurityContext 的作用

JWT filter 驗證成功後，會把 Authentication 放進：

```java
SecurityContextHolder.getContext()
    .setAuthentication(authentication);
```

SecurityContext 可以理解為目前 request 的安全上下文，裡面記錄：

- 目前使用者是誰
- 使用者是否已經 authenticated
- 使用者有哪些 authorities 或 roles

後續 Spring Security authorization 流程會讀取這個 context，判斷 request 是否可以繼續。

## 6. Request matcher 與 authorization rules

在 [SecurityConfig.java](backend/src/main/java/com/devsecops/taskapp/config/SecurityConfig.java) 中，可以用 request matcher 定義 API 規則：

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers("/api/auth/login").permitAll()
        .requestMatchers("/api/users/**").hasRole("ADMIN")
        .requestMatchers("/api/tasks/**").authenticated()
        .anyRequest().authenticated())
```

意思是：

- `/api/auth/login`：不需要先登入
- `/api/users/**`：需要 ADMIN role
- `/api/tasks/**`：只要是有效登入使用者即可
- 其他 request：預設需要 authentication

要注意：

> JwtAuthenticationFilter 負責驗證 token 並建立 Authentication；它不是直接負責判斷 request matcher 規則。

實際流程是：

```text
JWT filter
    ↓
驗證 token，建立 Authentication
    ↓
放入 SecurityContext
    ↓
Spring Security authorization filter
    ↓
比對 request matcher rules
    ↓
允許或拒絕 request
    ↓
Controller
```

## 7. `@PreAuthorize`

除了在 SecurityConfig 使用 request matcher，也可以在 Controller method 使用 method-level authorization：

```java
@PreAuthorize("hasRole('ADMIN')")
public ResponseEntity<Void> delete(@PathVariable Long id) {
    ...
}
```

這需要啟用：

```java
@EnableMethodSecurity
```

request matcher 和 `@PreAuthorize` 的差異：

- request matcher：以 URL path 和 HTTP request 為主
- `@PreAuthorize`：以 controller method 的執行條件為主

例如：

```java
.requestMatchers("/api/users/**").hasRole("ADMIN")
```

可以保護整個 users resource；而：

```java
@PreAuthorize("hasRole('ADMIN')")
```

可以針對某一個 method 再加一層保護。

## 8. 401 與 403

| 狀態碼 | 意義 | 例子 |
| --- | --- | --- |
| `401 Unauthorized` | 沒有有效身份 | 沒有 token、token 過期、token 無效 |
| `403 Forbidden` | 已經有身份，但權限不足 | USER 存取 ADMIN-only API |
| `200 OK` | 身份與權限都符合 | ADMIN 讀取 `/api/users` |

可用以下方式設定不同回應：

```java
.exceptionHandling(exceptions -> exceptions
        .authenticationEntryPoint((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
        .accessDeniedHandler((request, response, exception) ->
                response.sendError(HttpServletResponse.SC_FORBIDDEN)))
```

## 9. 最後的整體理解

```text
Frontend
  ├─ router guard：改善頁面導航體驗
  ├─ hide button：改善畫面體驗
  └─ API client：附加 Bearer token

Backend
  ├─ JwtAuthenticationFilter：驗證 JWT，建立身份
  ├─ SecurityContext：保存目前 request 的身份與 authorities
  ├─ requestMatchers：保護 URL/API
  ├─ @PreAuthorize：保護特定 method
  └─ Controller：只有通過安全檢查後才會執行
```

一句話總結：

> Frontend 負責改善使用者體驗；backend 的 JWT filter 負責建立身份；Spring Security 的 authorization rules 和 `@PreAuthorize` 負責真正的權限控制。
