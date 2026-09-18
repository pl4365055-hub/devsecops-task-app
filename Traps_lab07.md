# Lab 07 - Automated Testing 踩坑

本文記錄使用 Maven、Spring Boot Test、Testcontainers 與 Docker Desktop 執行自動化測試時遇到的問題。

## 1. `mvn` 找不到

### 問題

```text
mvn : The term 'mvn' is not recognized
```

### 原因

Windows 沒有安裝全域 Maven，或 Maven 沒有加入 PATH。

### 解法

本專案已提供本地 JDK、Maven 與啟動腳本：

```powershell
cd backend
.\mvn-local.ps1 test
```

腳本會自動設定 `JAVA_HOME`，並使用 `backend/.tools` 內的 JDK 和 Maven。

## 2. 測試使用了已不存在的類別

### 問題

舊版 `AuthServiceTest` 引用了 `AuthService`、`JwtUtil` 等目前專案不存在的類別，導致 testCompile 失敗。

### 解法

測試必須跟目前 production code 的責任保持一致。本專案目前由 `AuthController`、`UserService`、`JwtService` 負責登入流程，因此將單元測試改為驗證 `JwtService` 產生及解析 token。

## 3. Testcontainers 找不到 Docker environment

### 問題

```text
Could not find a valid Docker environment
```

Docker CLI 可以執行，但 Testcontainers 仍然失敗。

### 排查

先確認 Docker Desktop daemon：

```powershell
docker context use desktop-linux
docker info
docker run --rm hello-world
```

Windows 上 Testcontainers 可透過 named pipe 連接 Docker Engine，不代表一定要使用 TCP。

## 4. Testcontainers 被錯誤的 named pipe 設定攔截

### 問題

`%USERPROFILE%\.testcontainers.properties` 曾被設定為：

```properties
docker.client.strategy=org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy
```

錯誤的 pipe 會使 Testcontainers 連到 `docker_cli`，即使 Docker CLI 使用的是 `desktop-linux` context 也會失敗。

### 解法

使用 Docker Desktop 可用的 pipe：

```properties
docker.client.strategy=org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy
docker.host=npipe:////./pipe/docker_engine
```

也可以先移除強制策略，讓 Testcontainers 使用 `DOCKER_HOST` 或預設策略。

## 5. Docker API version 太舊

### 問題

```text
client version 1.32 is too old
Minimum supported API version is 1.40
```

Docker Desktop daemon 可用，但舊版 docker-java client 使用 API 1.32。

### 解法

升級 Testcontainers，並設定 docker-java API version：

```xml
<version>1.21.3</version>
```

`junit-jupiter` 和 `postgresql` 使用相同版本。

在 `%USERPROFILE%\.docker-java.properties` 設定：

```properties
api.version=1.40
```

## 6. `schema.sql` 沒有自動執行

### 問題

```text
ERROR: relation "tasks" does not exist
```

Testcontainers 啟動的是非 embedded PostgreSQL。Spring Boot 不一定會自動執行 `schema.sql`。

### 解法

在 `src/test/resources/application-test.yml`：

```yaml
spring:
  sql:
    init:
      mode: always
```

並在整合測試啟用 profile：

```java
@ActiveProfiles("test")
```

YAML 只能使用空白縮排，不能混入 tab。

## 7. API 回傳 401

### 問題

PostgreSQL 和 Spring Boot 都已啟動，但建立 task 的測試得到：

```text
expected: <200> but was: <401>
```

### 原因

`/api/tasks/**` 需要有效 JWT。整合測試直接呼叫 API，沒有提供 Bearer token。

### 解法

測試先呼叫公開的 login API：

```java
ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
    "/api/auth/login",
    new LoginRequest("user", "password"),
    LoginResponse.class
);
```

再把 token 放進 request header：

```java
headers.setBearerAuth(loginResponse.getBody().token());
```

不要在測試中關閉 Security，否則測試不到真實的 authentication flow。

## 8. 測試診斷順序

遇到整合測試失敗時，依序確認：

1. Maven/JDK 是否可執行
2. Docker Desktop daemon 是否可用
3. Testcontainers 是否能連 Docker Engine
4. Docker API version 是否相容
5. PostgreSQL container 是否啟動
6. schema 是否初始化
7. Spring ApplicationContext 是否載入
8. API 是否帶有正確 JWT

這個順序可以把環境問題、測試設定問題和 application logic 問題分開。
