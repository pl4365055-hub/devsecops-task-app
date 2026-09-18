# Lab 07 - Automated Testing

## 1. 學習目標

- 使用 Maven 執行 Spring Boot unit test 和 integration test
- 使用 Testcontainers 建立一次性的 PostgreSQL 測試環境
- 使用 Docker Desktop named pipe 連接本機 Docker Engine
- 讓 Spring Boot Test 自動初始化測試資料庫 schema
- 在整合測試中驗證 JWT login 和受保護 API

## 2. 測試分層

```text
Unit Test
  └─ 不需要 Docker，快速驗證單一 service 或 utility

Integration Test
  ├─ SpringBootTest 啟動 application context
  ├─ Testcontainers 啟動 PostgreSQL
  ├─ schema.sql 初始化資料表
  ├─ Login 取得 JWT
  └─ 呼叫受保護的 task API
```

本次整合測試使用：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
```

## 3. 專案工具

本專案不依賴系統全域 Maven，backend 已提供：

```text
backend/.tools/jdk/
backend/.tools/maven/
backend/mvn-local.ps1
```

在 PowerShell 執行：

```powershell
cd backend
.\mvn-local.ps1 test
```

## 4. Docker Desktop named pipe

Docker Desktop 必須正在執行，並使用 Linux engine：

```powershell
docker context use desktop-linux
docker info
docker run --rm hello-world
```

Testcontainers 可透過 Windows named pipe 連接 Docker Engine，不需要開啟 TCP 2375。

使用者層設定檔 `%USERPROFILE%\.testcontainers.properties`：

```properties
docker.client.strategy=org.testcontainers.dockerclient.NpipeSocketClientProviderStrategy
docker.host=npipe:////./pipe/docker_engine
```

Docker Desktop 29.7.2 需要較新的 Docker API。`%USERPROFILE%\.docker-java.properties`：

```properties
api.version=1.40
```

## 5. Maven dependencies

`backend/pom.xml` 使用 Testcontainers：

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.21.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.21.3</version>
    <scope>test</scope>
</dependency>
```

`postgresql` module 會提供 `PostgreSQLContainer`，`junit-jupiter` module 讓 `@Testcontainers` 和 `@Container` 整合 JUnit 5。

## 6. Testcontainers PostgreSQL

測試類別建立一次性的 PostgreSQL：

```java
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test");
```

使用動態資料庫連線資訊注入 Spring：

```java
@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
}
```

測試結束後，Testcontainers 會停止並清理 container。

## 7. 初始化 schema

測試資料庫使用 production 的 `schema.sql`，但測試 profile 必須明確開啟 SQL initialization：

```yaml
spring:
  sql:
    init:
      mode: always
```

檔案位置：

```text
backend/src/test/resources/application-test.yml
```

整合測試啟用：

```java
@ActiveProfiles("test")
```

這樣 `users`、`tasks` 表和測試帳號會在 PostgreSQL container 建立後初始化。

## 8. 驗證 JWT 保護的 API

`/api/tasks/**` 需要登入，因此測試不能直接 POST task。測試流程是：

```text
POST /api/auth/login
        ↓
取得 LoginResponse.token()
        ↓
Authorization: Bearer <token>
        ↓
POST /api/tasks
        ↓
確認 HTTP 200 和資料已寫入 PostgreSQL
```

核心測試程式：

```java
ResponseEntity<LoginResponse> loginResponse = restTemplate.postForEntity(
    "/api/auth/login",
    new LoginRequest("user", "password"),
    LoginResponse.class
);

HttpHeaders headers = new HttpHeaders();
headers.setContentType(MediaType.APPLICATION_JSON);
headers.setBearerAuth(loginResponse.getBody().token());

ResponseEntity<Task> response = restTemplate.postForEntity(
    "/api/tasks",
    new HttpEntity<>(task, headers),
    Task.class
);
```

最後除了驗證 HTTP status，也直接查詢 mapper：

```java
Task saved = taskMapper.selectById(response.getBody().getId());
assertNotNull(saved);
assertEquals("Integration Test", saved.getTitle());
```

這同時驗證 API response 和資料庫 persistence。

## 9. 執行測試

只執行 unit tests：

```powershell
cd backend
.\mvn-local.ps1 "-Dtest=AuthServiceTest,TaskServiceTest" test
```

只執行整合測試：

```powershell
.\mvn-local.ps1 "-Dtest=TaskApiIntegrationTest" test
```

執行全部 backend tests：

```powershell
.\mvn-local.ps1 clean test
```

預期結果：

```text
AuthServiceTest: 1 passed
TaskServiceTest: 3 passed
TaskApiIntegrationTest: 1 passed
BUILD SUCCESS
```

## 10. 測試生命週期

```text
Maven test
   ↓
JUnit 啟動測試
   ↓
Testcontainers 連接 Docker Desktop named pipe
   ↓
啟動 PostgreSQL 16 container
   ↓
SpringBootTest 載入 application context
   ↓
執行 schema.sql
   ↓
Login 取得 JWT
   ↓
呼叫受保護 API
   ↓
驗證資料庫 persistence
   ↓
清理 container
```

## 11. 完成檢查表

- [ ] Docker Desktop 正在執行
- [ ] Docker context 為 `desktop-linux`
- [ ] `docker run --rm hello-world` 成功
- [ ] named pipe 可被 Testcontainers 使用
- [ ] Docker API version 至少為 `1.40`
- [ ] Testcontainers dependencies 使用相容版本
- [ ] test profile 開啟 `spring.sql.init.mode=always`
- [ ] integration test 使用 `@ActiveProfiles("test")`
- [ ] API request 帶有 Bearer JWT
- [ ] `clean test` 顯示 `BUILD SUCCESS`
