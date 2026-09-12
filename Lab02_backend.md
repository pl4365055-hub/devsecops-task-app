# Lab 02 - Backend 技术梳理

## 1. 技术栈总览

本项目 backend 使用以下技术：

| 技术 | 作用 |
| --- | --- |
| Java 17 | Spring Boot 3.2 的运行环境 |
| Spring Boot 3.2 | 应用启动、Web 和自动配置 |
| Spring Web | REST API、Controller、JSON |
| Spring Security | 认证、授权和请求过滤器 |
| JWT / JJWT 0.12.3 | 登录后签发和验证 Token |
| BCrypt | 用户密码哈希校验 |
| MyBatis-Plus 3.5.7 | 数据库 CRUD 和 Mapper |
| MyBatis-Spring 3.0.3 | 连接 MyBatis 与 Spring 6 |
| PostgreSQL 16 | 关系型数据库 |
| Docker Compose | 启动本地 PostgreSQL |
| Maven 3.9.11 | 编译、打包、启动项目 |

本项目使用 `backend/.tools` 保存项目专用工具：

```text
backend/.tools/jdk/       Java 17
backend/.tools/maven/     Maven 3.9.11
backend/mvn-local.ps1     自动设置 JAVA_HOME 后执行 Maven
```

## 2. Backend 目录结构

```text
backend/
├─ db/                         旧数据库脚本目录，不作为当前初始化入口
├─ resources/
│  ├─ application.yml         Spring Boot 配置
│  └─ schema.sql              PostgreSQL 初始化脚本
├─ src/main/java/com/devsecops/taskapp/
│  ├─ config/SecurityConfig.java
│  ├─ controller/
│  │  ├─ AuthController.java
│  │  ├─ UserController.java
│  │  └─ TaskController.java
│  ├─ dto/                    请求和响应对象
│  ├─ entity/                 User、Task 数据实体
│  ├─ mapper/                 MyBatis-Plus Mapper
│  ├─ security/
│  │  ├─ JwtService.java
│  │  └─ JwtAuthenticationFilter.java
│  ├─ service/                业务逻辑
│  └─ TaskAppApplication.java 启动类
├─ pom.xml
└─ mvn-local.ps1
```

## 3. 必知必会

### 3.1 启动 PostgreSQL

在项目根目录执行：

```powershell
docker compose up -d postgres
```

Compose 使用：

```text
./backend/resources/schema.sql
```

初始化内容包括：

- `users` 表
- `tasks` 表
- `admin/password` 管理员账号
- `user/password` 普通账号

停止容器：

```powershell
docker compose down
```

删除容器和数据卷并重新初始化：

```powershell
docker compose down -v
docker compose up -d postgres
```

注意：PostgreSQL 的 `/docker-entrypoint-initdb.d/` 脚本只在空数据目录第一次启动时执行。

### 3.2 启动和编译 Backend

```powershell
cd backend
.\mvn-local.ps1 spring-boot:run
```

编译打包：

```powershell
.\mvn-local.ps1 -q -DskipTests package
```

运行环境：

```text
Backend: http://localhost:8080
Java:    17
Maven:   3.9.11
```

### 3.3 配置文件

`backend/src/main/resources/application.yml` 控制运行配置：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:5432/taskdb
```

本机运行时：

```text
DB_HOST=localhost
```

Docker 网络内运行 backend 时：

```text
DB_HOST=postgres
```

可用环境变量：

```powershell
$env:DB_HOST = "localhost"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
$env:JWT_SECRET = "change-this-to-a-long-secret"
```

生产环境不能使用默认 JWT secret。

### 3.4 Controller、Service、Mapper 分层

请求流程：

```text
HTTP Request
    ↓
Controller      接收参数、返回 HTTP 响应
    ↓
Service         业务逻辑
    ↓
Mapper          数据库访问
    ↓
PostgreSQL
```

职责原则：

- Controller 不应该直接写复杂 SQL
- Service 负责业务判断和流程组合
- Mapper 负责数据库查询
- Entity 表示数据库表结构
- DTO 控制对外暴露的字段

### 3.5 登录流程

`POST /api/auth/login` 的流程：

1. 接收 username 和 password
2. 通过 `UserMapper` 查询用户
3. 使用 `PasswordEncoder` 校验 BCrypt 密码
4. 使用 `JwtService` 签发 JWT
5. 返回 Token 和脱敏后的用户信息

PowerShell 调用：

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
```

### 3.6 JWT 请求认证

请求其他接口时：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/users" `
  -Headers @{ Authorization = "Bearer $token" }
```

过滤器的工作：

1. 读取 `Authorization` Header
2. 确认值以 `Bearer ` 开头
3. 解析并验证签名和过期时间
4. 读取 username 和 role
5. 建立 Spring Security Authentication
6. 交给后续 Controller 处理

### 3.7 权限控制

删除用户接口：

```text
DELETE /api/users/{id}
```

代码使用：

```java
@PreAuthorize("hasRole('ADMIN')")
```

JWT 中的角色会转换成 Spring Security authority：

```text
ADMIN -> ROLE_ADMIN
USER  -> ROLE_USER
```

因此数据库 role 建议保存为 `ADMIN` 或 `USER`，不要混用不同命名规则。

### 3.8 API 清单

认证：

```text
POST /api/auth/login
```

用户：

```text
GET    /api/users
DELETE /api/users/{id}       ADMIN only
```

任务：

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}
```

## 4. 难点重点

### 4.1 Java 版本和 Maven 版本

Spring Boot 3.2 要求 Java 17。系统原本只有 Java 8 且没有 Maven，因此不能直接执行：

```powershell
mvn spring-boot:run
```

本项目改用：

```powershell
.\mvn-local.ps1 spring-boot:run
```

脚本会自动设置项目内的 `JAVA_HOME` 和 Maven 路径。

### 4.2 MyBatis-Spring 版本兼容

曾出现：

```text
Invalid value type for attribute 'factoryBeanObjectType': java.lang.String
```

原因是旧版 `mybatis-spring` 与 Spring Framework 6.1 不兼容。当前固定：

```xml
mybatis-plus: 3.5.7
mybatis-spring: 3.0.3
```

查看实际依赖：

```powershell
.\mvn-local.ps1 dependency:tree '-Dincludes=org.mybatis:mybatis-spring'
```

### 4.3 本机和 Docker 的数据库 Host 不同

`postgres` 是 Docker Compose 网络中的服务名，Windows 主机直接运行 backend 时无法解析：

```text
UnknownHostException: postgres
```

当前配置使用：

```yaml
url: jdbc:postgresql://${DB_HOST:localhost}:5432/taskdb
```

- 本机 backend：默认 `localhost`
- Docker 内 backend：设置 `DB_HOST=postgres`

### 4.4 数据库初始化脚本只执行一次

Compose 挂载：

```text
backend/resources/schema.sql
```

修改 SQL 后，旧 volume 不会自动重新执行。需要：

```powershell
docker compose down -v
docker compose up -d postgres
```

这会删除本地数据库数据，只适合开发环境。

### 4.5 密码不能明文保存

数据库中的 password 必须是 BCrypt hash：

```text
$2a$10$...
```

登录时由：

```java
passwordEncoder.matches(rawPassword, encodedPassword)
```

进行验证。不能直接比较明文，也不能把密码放入用户列表响应。

### 4.6 启动成功不代表 API 一定成功

Spring Boot 可以在数据库尚未访问时正常启动。真正访问 `/api/auth/login`、`/api/users` 或 `/api/tasks` 时，才可能暴露数据库连接错误。因此要分别检查：

1. Tomcat 是否监听 8080
2. PostgreSQL 容器是否运行
3. 5432 是否开放
4. 数据库 Host 是否正确
5. 表和用户是否已初始化

## 5. 错误集

### 错误 1：`mvn is not recognized`

原因：系统没有 Maven，或 PATH 没配置。

解决：

```powershell
cd backend
.\mvn-local.ps1 -version
```

### 错误 2：Java 版本是 8

典型情况：

```text
java version "1.8..."
```

Spring Boot 3.2 需要 Java 17。不要只修改 PATH 后直接猜测，使用：

```powershell
.\mvn-local.ps1 -version
```

确认 Maven 输出的 Java 版本是 17。

### 错误 3：`UnknownHostException: postgres`

原因：backend 在 Windows 主机运行，却使用了 Docker 内部服务名 `postgres`。

解决：

```powershell
$env:DB_HOST = "localhost"
.\mvn-local.ps1 spring-boot:run
```

并确认 PostgreSQL 容器已启动：

```powershell
docker compose up -d postgres
```

### 错误 4：登录返回 `401 Unauthorized`

可能原因：

- 用户不存在
- 密码错误
- schema 初始化没有执行
- 旧 volume 中仍保存旧密码 hash

开发环境重建：

```powershell
docker compose down -v
docker compose up -d postgres
```

然后使用：

```text
admin / password
```

### 错误 5：登录返回 `403 Forbidden`

检查：

- `/api/auth/login` 是否配置为 `permitAll()`
- 是否请求到了旧的 backend 进程
- 是否有旧 Java 进程占用 8080

查看端口：

```powershell
Get-NetTCPConnection -LocalPort 8080 -State Listen
```

### 错误 6：`factoryBeanObjectType` 错误

原因：MyBatis-Spring 旧版本和 Spring 6.1 冲突。

检查：

```powershell
.\mvn-local.ps1 dependency:tree '-Dincludes=org.mybatis:mybatis-spring'
```

要求使用 MyBatis-Spring 3.x。

### 错误 7：重复 `package` 声明

典型编译错误：

```text
class, interface, enum, or record expected
```

检查 Java 文件开头是否重复出现：

```java
package com.devsecops.taskapp.mapper;
package com.devsecops.taskapp.mapper;
```

每个 Java 文件只能有一个 package 声明。

### 错误 8：启动配置没有生效

Spring Boot 默认读取：

```text
backend/src/main/resources/application.yml
```

不要只把配置放在：

```text
backend/resources/application.yml
```

后者可以作为文档或数据库脚本目录，但不是标准 classpath resources 目录。

### 错误 9：数据库初始化脚本修改后没有变化

原因是 PostgreSQL volume 仍然存在。确认脚本挂载的是：

```text
./backend/resources/schema.sql
```

必要时重建 volume：

```powershell
docker compose down -v
docker compose up -d postgres
```

## 6. 检查清单

- [ ] Docker Desktop 正常运行
- [ ] `docker compose up -d postgres` 成功
- [ ] PostgreSQL 容器名为 `devsecops-task-postgres`
- [ ] `backend/resources/schema.sql` 挂载正确
- [ ] Java 版本为 17
- [ ] Maven 版本为 3.9.11
- [ ] `mybatis-spring` 版本为 3.x
- [ ] `DB_HOST` 本机使用 `localhost`
- [ ] backend 监听 8080
- [ ] `admin/password` 登录返回 JWT
- [ ] 请求 Header 使用 `Authorization: Bearer <token>`
- [ ] 删除用户时使用 ADMIN Token
