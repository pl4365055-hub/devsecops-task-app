# DevSecOps Task App

一个 Vue + Spring Boot + PostgreSQL 的任务管理应用，包含 JWT 登录、用户管理和任务管理功能。

## 项目结构

```text
├─ backend/       Spring Boot API
├─ frontend/      Vue + Vite 前端
├─ docs/          项目文档
├─ docker-compose.yml
└─ README.md
```

## 环境要求

- Docker Desktop
- Node.js 和 npm
- 不需要系统安装 Java/Maven，backend 已提供项目本地工具：
  - Java 17：`backend/.tools/jdk/`
  - Maven 3.9.11：`backend/.tools/maven/`

## 启动 PostgreSQL

在项目根目录执行：

```powershell
docker compose up -d postgres
```

数据库初始化脚本位于：

```text
backend/resources/schema.sql
```

该脚本会创建 `users` 和 `tasks` 表，并建立测试账号：

```text
username: admin
password: password
role: ADMIN
```

注意：PostgreSQL 只会在首次创建 volume 时执行初始化脚本。

如需重新初始化数据库，请确认没有需要保留的数据后执行：

```powershell
docker compose down -v
docker compose up -d postgres
```

## Backend

进入 backend：

```powershell
cd backend
```

启动后端：

```powershell
.\mvn-local.ps1 spring-boot:run
```

后端地址：

```text
http://localhost:8080
```

本机运行时数据库默认连接 `localhost:5432`。如需连接其他主机，可以设置：

```powershell
$env:DB_HOST = "localhost"
$env:DB_USERNAME = "postgres"
$env:DB_PASSWORD = "postgres"
```

编译项目：

```powershell
.\mvn-local.ps1 -q -DskipTests package
```

### Backend API

登录：

```text
POST /api/auth/login
```

PowerShell 示例：

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

查询用户：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/users" `
  -Headers @{ Authorization = "Bearer $token" }
```

删除用户，仅 ADMIN：

```powershell
Invoke-RestMethod `
  -Uri "http://localhost:8080/api/users/2" `
  -Method DELETE `
  -Headers @{ Authorization = "Bearer $token" }
```

任务 API：

```text
GET    /api/tasks
POST   /api/tasks
GET    /api/tasks/{id}
PUT    /api/tasks/{id}
DELETE /api/tasks/{id}
```

除登录接口外，API 请求需要在 Header 中携带：

```text
Authorization: Bearer <JWT_TOKEN>
```

## Frontend

进入 frontend：

```powershell
cd frontend
```

安装依赖：

```powershell
npm install
```

启动开发服务器：

```powershell
npm run dev
```

前端登录页面：

```text
http://localhost:5173/login
```

主要页面：

- `/login`：用户名密码登录
- `/dashboard`：侧边导航和欢迎信息
- `/tasks`：任务列表、新增、编辑和删除

构建生产版本：

```powershell
npm run build
```

## 推荐启动顺序

分别打开三个终端：

```powershell
# Terminal 1 - 项目根目录
docker compose up -d postgres
```

```powershell
# Terminal 2 - backend
cd backend
.\mvn-local.ps1 spring-boot:run
```

```powershell
# Terminal 3 - frontend
cd frontend
npm run dev
```

然后打开：

```text
http://localhost:5173/login
```
