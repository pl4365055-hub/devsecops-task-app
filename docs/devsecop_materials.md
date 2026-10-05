# DevSecOps Task App —— 專家評審 × 學習材料

> 評審對象：Vue 3 + Spring Boot 3 + PostgreSQL 的任務管理應用，涵蓋本地 JWT 登入、Keycloak SSO、Docker Compose 多環境、自動化測試與 GitHub Actions CI/CD。
>
> 閱讀建議：第 1 章建立全景 → 第 2 章按模組鑽研技術重點 → 第 3 章背誦必知必會 → 第 4 章對照踩坑 → 第 5 章看專家評審 → 第 6 章規劃優化。文末附自測題。

---

## 目錄

1. [項目全景與技術棧](#1-項目全景與技術棧)
2. [技術重點（七大模組）](#2-技術重點七大模組)
3. [必知必會清單](#3-必知必會清單)
4. [踩坑全集（按層分類）](#4-踩坑全集按層分類)
5. [DevSecOps 專家評審](#5-devsecops-專家評審)
6. [未來優化路線圖](#6-未來優化路線圖)
7. [自測題](#7-自測題)

---

## 1. 項目全景與技術棧

### 1.1 架構圖

```text
┌─────────────────────────────────────────────────────────────┐
│  Browser                                                   │
│  Vue 3 SPA（Login / Dashboard / Tasks / Users）            │
└───────────┬───────────────────────────┬────────────────────┘
            │ local 模式                 │ sso 模式（prod）
            │ POST /api/auth/login      │ 跳轉 Keycloak 登入頁
            ▼                           ▼ (OIDC Auth Code + PKCE)
┌──────────────────────────┐      ┌──────────────────────────┐
│ Nginx (frontend 容器)     │      │ Keycloak 26（獨立 compose）│
│ 靜態資源 + /api 反向代理  │      │ realm=taskapp            │
└───────────┬──────────────┘      │ client=task-app (public) │
            │                      └───────────┬──────────────┘
            ▼                                   │ JWK 公鑰
┌──────────────────────────────────────────────▼─────────────┐
│ Spring Boot 3.2（backend 容器，非 root，JRE Alpine）        │
│  Security Filter Chain：                                   │
│   !prod → JwtAuthenticationFilter（HMAC 對稱密鑰自簽 JWT） │
│   prod  → OAuth2 Resource Server（JWK 驗章 + issuer 校驗） │
│  Controller：/api/auth、/api/users(ADMIN)、/api/tasks       │
└───────────┬────────────────────────────────────────────────┘
            ▼
┌───────────────────────────────────────────────────────────┐
│ PostgreSQL 16（schema.sql 初始化 users / tasks）           │
└───────────────────────────────────────────────────────────┘
```

### 1.2 技術棧一覽

| 層 | 技術 | 版本 | 用途 |
|---|---|---|---|
| 前端框架 | Vue + Vite | 3.5 / 8.3 | SPA、建構工具 |
| UI / 路由 | Element Plus、Vue Router | 2.x / 4.x | 元件庫、前端路由守衛 |
| 認證 SDK | keycloak-js | 26.x | OIDC 客戶端（PKCE） |
| HTTP | Axios | 1.x | 攔截器自動帶 token / 401 刷新 |
| 後端框架 | Spring Boot | 3.2.0（Java 17） | Web、Security、Validation、Actuator |
| OAuth2 | spring-boot-starter-oauth2-resource-server | 內建 | prod 下 JWT 驗章 |
| ORM | MyBatis-Plus + mybatis-spring | 3.5.7 / 3.0.3 | Mapper、BaseMapper CRUD |
| JWT | JJWT | 0.12.3 | dev/uat 自簽 HS256 JWT |
| 資料庫 | PostgreSQL | 16-alpine | 持久化 |
| 容器 | Docker（multi-stage）、Compose v2 | — | 鏡像構建、多環境編排 |
| 測試 | JUnit5、Spring Boot Test、Testcontainers、Vitest | 1.21.3 / 5.x | 單元 + 整合 + 前端 E2E 組件 |
| CI/CD | GitHub Actions | — | ci.yml / deploy.yml |
| 安全工具 | Gitleaks、Semgrep、OWASP DC、Trivy、npm audit | — | Secret / SAST / SCA / Image Scan |

### 1.3 四套 Compose 與四個 Profile（核心設計）

| 對象 | 認證方式 | 憑證來源 | Token |
|---|---|---|---|
| `dev` profile + `docker-compose.dev.yml` | Mock 登入（**密碼任意**） | 記憶體假使用者；任務仍用 PG | 應用自簽 JWT |
| `uat` profile（預設 compose） | 帳密登入 | PG `users` 表（schema.sql，BCrypt） | 應用自簽 JWT |
| `prod` profile + `docker-compose.prod.yml` | Keycloak SSO | Keycloak 簽發、後端 JWK 驗章 | Keycloak JWT |
| `test` profile | 同 uat | Testcontainers 起的 PG | 應用自簽 JWT |

**Build Once, Deploy Many**：同一個不可變鏡像 tag（`v2`），環境差異完全由 `SPRING_PROFILES_ACTIVE` + 環境變數決定；prod compose 只有 `image:` 沒有 `build:`，敏感值（DB 密碼、JWT secret）永不打包進鏡像。

### 1.4 一次請求的生命週期

```text
axios request 攔截器：localStorage 取 token → 加 Bearer（/auth/ 端點除外）
   ▼
Nginx：/api/ 反代到 backend:8080
   ▼
Security Filter Chain：驗簽 + 驗過期（prod 再驗 issuer）→ 轉換角色
   ▼
授權：requestMatchers / @EnableMethodSecurity（hasRole ADMIN）
   ▼
Controller → Service（MyBatis-Plus）→ PostgreSQL
   ▼
401：prod 攔截器先 refresh token 重放；失敗/本地模式 → 登出回登入頁
403：留在當前頁（權限不足，不登出）
```

---

## 2. 技術重點（七大模組）

### 2.1 前端：Vue 3 + Vite 的工程化要點

- **兩個正交維度（最容易搞混）**：
  - `isSso()`（local / sso）回答「用**哪種方式**登入」，App 啟動時查 `GET /api/auth/config` 才知道；
  - `isAuthenticated()`（有無 token）回答「**現在**是否已登入」，每次跳頁即時判斷。
  - 真正的分支條件是「有沒有 token」；isSso 只在需要登入時決定導向哪個登入頁。
- **認證模式運行時發現**：`AuthConfigController` 回 `{mode, keycloakUrl, realm, clientId}`，前端不把任何環境位址寫死 → 同一個前端 dist 可部署所有環境。
- **Axios 雙攔截器**：
  - Request：排除 `/auth/` 端點，避免登入請求帶舊 token；
  - Response：SSO 下 401 先 `updateToken(30)` 靜默刷新並**重放原請求**，用 `isRefreshing` 鎖防並發；刷新失敗才 logout；local 模式 401 清 localStorage 回 `/login`；**403 不登出**。
- **Router guard 只是 UX，不是安全邊界**：`requiresAuth` / `requiresAdmin` meta + `beforeEach`。任何人用 curl 帶 token 都能直達 API。
- SPA 部署關鍵：Nginx `try_files $uri $uri/ /index.html;`，否則刷新子路由 404。

### 2.2 後端：Spring Boot 3 + Spring Security 6

- **Profile 驅動的雙 Security 配置**（條件化 Bean 的經典用法）：
  - `@Profile("!prod")` → `DefaultSecurityConfig`：無狀態 + 自訂 `JwtAuthenticationFilter`（加在 `UsernamePasswordAuthenticationFilter` 之前）；
  - `@Profile("prod")` → `KeycloakSecurityConfig`：無狀態 + `oauth2ResourceServer().jwt()`。
  - 兩者共用同一套授權規則：`/api/auth/**` permitAll、`/actuator/health/**` permitAll、`/api/users/**` hasRole ADMIN、`/api/tasks/**` authenticated。
- **認證服務策略模式**：`AuthenticationService` 接口 + `MockAuthenticationService`（dev）/ `DbAuthenticationService`（uat/test：BCrypt 驗密 → JwtService 發 token）；prod 下 `AuthController` 本身 `@Profile("!prod")` 不載入，登入端點回 404，攻擊面收縮。
- **401/403 顯式分離**：自訂 `authenticationEntryPoint`（401）與 `accessDeniedHandler`（403）。Spring 預設對匿名請求可能回 403，不設定就會語義混亂。
- **角色前綴**：Spring 的 `hasRole("ADMIN")` 實際匹配 authority `ROLE_ADMIN`。自簽 JWT filter 與 `KeycloakRoleConverter` 都要顯式補 `ROLE_`。
- **Keycloak 角色映射**：從 `realm_access.roles` 取角色，過濾 `default-roles-taskapp`、`offline_access`、`uma_authorization` 等內建角色。
- **`@EnableMethodSecurity`**：方法級 `@PreAuthorize` 與路徑級授權構成縱深。
- **Actuator 最小暴露**：只開 `health`，`show-details: when_authorized`，避免洩漏內部組件資訊。

### 2.3 JWT：自簽 HMAC vs Keycloak JWK

| | dev/uat 自簽 | prod Keycloak |
|---|---|---|
| 算法 | HS256（對稱，JWT_SECRET ≥ 32 bytes / 256 bits） | RS256（非對稱，私鑰在 Keycloak） |
| 後端角色 | 驗帳密 + 發 token + 驗 token | 只驗 token（Resource Server），完全不碰密碼 |
| 密鑰取得 | 環境變數注入 | 啟動時從 JWK Set URI 拉公鑰 |
| 校驗項 | 簽名 + 過期 | 簽名 + 過期 + **issuer** |

- JJWT 0.12 API：`Jwts.builder().subject().claim().signWith(key)`；`Jwts.parser().verifyWith(key).build().parseSignedClaims()`。
- issuer-uri（對外，與 token 中 `iss` 一致）與 jwk-set-uri（容器內部抓取位址）刻意分開——見 2.5 的網路坑。

### 2.4 Docker：多階段構建與鏡像瘦身

- **後端**：`maven:3.9-eclipse-temurin-17` 構建 → 只 COPY 一個 jar 到 `eclipse-temurin:17-jre-alpine`；**非 root 使用者**（addgroup/adduser + USER）；內建 `HEALTHCHECK` 打 `/actuator/health`。
- **前端**：`node:20-alpine` 構建 → 只 COPY `dist/` 到 `nginx:alpine`，運行時連 Node 都沒有。
- **為什麼小 = 安全**：沒有 JDK/Maven/原始碼，編譯器與攻擊工具不進入運行鏡像；Trivy 可利用面更少；拉取部署更快。實測後端 700+MB → 300MB 級，前端 190+MB → 90MB 級。
- **緩存紀律**：先 COPY `pom.xml` / `package*.json` 跑依賴下載，再 COPY 源碼——改業務代碼不會讓依賴層緩存失效。
- `.dockerignore` 排除 target、.git、IDE 文件，避免上下文膨脹與敏感文件誤入。
- 區分兩種垃圾：`<none>` dangling image → `docker image prune`；BuildKit cache → `docker builder prune`（`docker buildx du` 查看）。

### 2.5 Docker Compose：多環境編排

- **健康檢查 + 啟動順序**：`depends_on: condition: service_healthy` + `pg_isready`，避免後端在 DB 未就緒時啟動（裸 `depends_on` 只保證啟動順序、不保證就緒）。
- **dev compose 的 Hot Reload**：
  - 後端直接用 `maven:3.9` 鏡像 + bind mount 源碼 + JDWP 5005 遠程調試端口 + devtools；
  - 前端 `node:20-alpine` + bind mount，匿名 volume `/app/node_modules` 遮蔽宿主目錄（避免 Windows 的 node_modules 進容器），`CHOKIDAR_USEPOLLING=true` 解決容器內文件事件。
- **Keycloak 跨 compose project 的網路坑（本項目最精妙的細節）**：
  - Keycloak 與 prod 應用是**獨立 compose project、獨立 network**，不能用服務名互相解析；
  - 所以後端經 `host.docker.internal:9080`（宿主映射埠）抓 JWK，並用 `extra_hosts: host.docker.internal:host-gateway` 兼容 Linux 原生 Docker；
  - 對外 issuer 仍寫 `http://localhost:9080/realms/taskapp`（與 token 的 iss 一致），形成「issuer 對外、JWK 走內部」的拆分。
- **PostgreSQL init 只跑一次**：`/docker-entrypoint-initdb.d/` 僅在數據目錄為空時執行；掛載用只讀 `:ro`。改 schema 後開發環境要 `down -v` 重建（生產禁止）。
- Compose v2 已忽略 `version:` 字段（obsolete 警告），可移除。
- prod compose 刻意不對外暴露 Postgres 端口；backend 設內存 limit 512M。

### 2.6 自動化測試

| 類型 | 工具 | 關鍵實踐 |
|---|---|---|
| 後端單元 | JUnit 5、AssertJ | 測當前代碼職責（JwtService、MockAuthenticationService、KeycloakRoleConverter） |
| 後端整合 | Spring Boot Test + **Testcontainers** | 每測試類起真實 PG 16 容器；`spring.sql.init.mode: always` 執行 schema.sql；`@ActiveProfiles("test")` |
| 安全流測試 | MockMvc / RestTemplate | **先調真實 login API 取 token 再帶 Bearer**，不關閉 Security，否則測不到認證鏈路 |
| 前端組件 | Vitest + @vue/test-utils + jsdom | Login 組件行為測試 |

- Testcontainers 的意義：消除「本機能跑、CI 不行」的環境漂移，數據庫版本與生產一致。
- 整合測試八字診斷順序：JDK/Maven → Docker daemon → Testcontainers 連接 → Docker API 版本 → PG 容器 → schema 初始化 → ApplicationContext → JWT。

### 2.7 CI/CD 與 DevSecOps 工具鏈

**ci.yml 流水線**：

```text
frontend-test (npm ci → lint → vitest → npm audit → build)
backend-test  (mvn test → OWASP DC → package)
        │ needs 兩者
        ├─ security-scan：Gitleaks（secret）+ Semgrep（SAST：OWASP Top10/Java-Spring/JavaScript）
        └─ docker-build：Buildx 構建 → Trivy 掃描（CRITICAL/HIGH）
```

**deploy.yml**：push `v*` tag → 構建推送 Registry（同時打 version tag + latest）→ `environment: production`（GitHub Environment 可掛審批人）→ SSH 到目標機執行 `deploy.sh`（pull + up，所有密鑰由 secrets 注入）。

**DevSecOps 四類掃描對照（必背）**：

| | SAST | SCA（依賴掃描） | Container Scan | Secret Scan |
|---|---|---|---|---|
| 掃什麼 | 你寫的源碼（危險寫法） | lockfile / pom（借來的零件 CVE） | 完整鏡像（OS 包 + 語言包） | 倉庫歷史與內容 |
| 何時 | 寫碼/CI | CI | build 後、部署前 | CI / pre-commit |
| 工具 | Semgrep、CodeQL、SonarQube | OWASP DC、npm audit、Dependabot | Trivy、Grype | Gitleaks、TruffleHog |
| 誤報 | 偏高 | 中 | 中 | 中低 |

- **漸進式門禁**：本項目 npm audit、OWASP DC、Trivy 目前都 `continue-on-error` / `exit-code: 0`，屬於「先觀察、只警告」階段——這是合理的落地策略：一上來全擋，開發者只會繞過。
- **OWASP DC 運行原理**：插件（運行在 runner 上的 Java 程序）向 NVD 拉 CVE 建本地 H2 庫 → 抽每個 jar 的 CPE 證據比對。GitHub 給機器、NVD 給數據、比對在 runner 本地。無 NVD API key 約 5 請求/30 秒，常超時，是流水線最慢環節。
- **Log4Shell 式論證**：有 SCA/鏡像掃描的團隊幾分鐘定位，沒有的人工翻幾天——這就是 DevSecOps 的價值。

---

## 3. 必知必會清單

### 3.1 概念題（能向別人講清楚）

1. 認證（Authentication，你是誰）vs 授權（Authorisation，你能做什麼）。
2. 401（未認證：無 token / 過期 / 密鑰不一致）vs 403（已認證但權限不足）。
3. JWT 結構：Header.Payload.Signature（Base64URL，非加密）；為什麼不能在 JWT 裡放敏感信息。
4. 對稱 HS256（雙方共享密鑰）vs 非對稱 RS256（私鑰簽、公鑰驗）的取捨。
5. OAuth2 四種角色：Resource Owner、Client、Authorization Server、Resource Server；OIDC 在 OAuth2 上加了 ID token。
6. Authorization Code + PKCE 流程：為什麼公共客戶端（SPA）要用 PKCE（`code_challenge` / `code_verifier`，S256），為什麼不用 Implicit。
7. SSO：`check-sso` 靜默登錄的含義；SSO logout 必須結束 Keycloak session。
8. Build Once Deploy Many：不可變鏡像 + 運行時配置；為什麼 tag 用版本號不用環境名。
9. SAST / DAST / SCA / Container Scan 的區別（DAST 看「程式怎麼跑」，SAST 看「怎麼寫」）。
10. 為什麼安全邊界永遠在後端：前端守衛、localStorage、隱藏菜單全都可繞過。
11. CVE / CPE / CVSS：有 CVE ≠ 一定被利用，要看代碼可達性（reachability）。
12. Shift-left 與安全門禁漸進收緊的策略。

### 3.2 技能題（能動手）

- [ ] 用四種 compose/profile 啟動並說明差異；能排查「容器內用 postgres、宿主用 localhost」。
- [ ] 用 curl/Invoke-RestMethod 完成 login → 帶 token 調 tasks → 驗證 users 的 ADMIN 限制。
- [ ] 寫一個 JJWT 產生/解析並解釋 WeakKeyException。
- [ ] 配置一個 Spring Security Filter Chain（含 401/403 handler、角色前綴）。
- [ ] 寫多階段 Dockerfile 並用 `docker history` 解釋層大小。
- [ ] 用 Testcontainers 寫整合測試，處理 schema 初始化。
- [ ] 看懂並修改 GitHub Actions：needs、if、secrets、environment 審批、Buildx cache。
- [ ] 用 gitleaks/semgrep/trivy 本地跑一次並讀報告。

### 3.3 常用命令速查

```powershell
# Compose
docker compose up --build -d          # 構建並後台啟動
docker compose ps ; docker compose logs --tail=100 backend
docker compose down                   # 停（留數據）
docker compose down -v                # 停並刪 volume（僅開發！）
docker compose -f docker-compose.keycloak.yml up -d
docker compose -f docker-compose.prod.yml up -d

# 鏡像 / 緩存
docker images ; docker history <img>
docker image prune                    # 清 dangling
docker buildx du ; docker builder prune

# 本地工具鏈
cd backend ; .\mvn-local.ps1 spring-boot:run
cd backend ; .\mvn-local.ps1 test
cd frontend ; npm run dev ; npm run build ; npx vitest run

# 登入驗證
$body = @{ username='admin'; password='password' } | ConvertTo-Json
$r = Invoke-RestMethod -Uri http://localhost:8080/api/auth/login -Method POST -ContentType 'application/json' -Body $body
Invoke-RestMethod -Uri http://localhost:8080/api/users -Headers @{ Authorization = "Bearer $($r.token)" }
```

### 3.4 端口對照

| 服務 | 宿主端口 |
|---|---|
| Frontend（prod Nginx） | 80 |
| Frontend dev（Vite） | 5173 |
| Backend | 8080 |
| Backend JDWP | 5005 |
| PostgreSQL | 5432 |
| Keycloak（宿主 9080 → 容器 8080） | 9080 |

---

## 4. 踩坑全集（按層分類）

> 以下每條均來自項目真實踩坑記錄（Traps_lab01–10）。格式：症狀 → 根因 → 解法。

### 4.1 環境與工具鏈

| # | 症狀 | 根因 | 解法 |
|---|---|---|---|
| 1 | Spring Boot 3 起不來 / `mvn` 找不到 | 系統只有 Java 8、無 Maven | 項目自帶 `.tools/jdk17` + `.tools/maven3.9`，用 `mvn-local.ps1`；確認 `-version` 顯示 17 |
| 2 | `factoryBeanObjectType: String` | 舊 mybatis-spring 2.x 與 Spring 6.1 不相容 | mybatis-plus 3.5.7 + mybatis-spring **3.0.3**；`dependency:tree` 驗證實際版本 |
| 3 | `Malformed POM: Unrecognised tag 'plugin'` | plugin 放到了 `</plugins>` 之外 | 放入 `<build><plugins>`；先 `mvn validate` |
| 4 | npm EPERM（全局 cache）/ Vite spawn EPERM | Windows 沙盤/文件鎖，非代碼問題 | 用 `--cache .npm-cache`；CI/Linux 上跑 |
| 5 | Vitest worker 崩：`markAsUncloneable is not a function` | jsdom 30→undici 8 需 Node 22.19+，CI 是 Node 20；npm engines 只警告 | 升 Node 22 **或** jsdom 降到 26（無 undici 依賴），重產 lockfile |
| 6 | `version is obsolete` 警告 | Compose v2 忽略 version 字段 | 移除 `version:`，按 Compose Specification |

### 4.2 Docker / Compose / 網路

| # | 症狀 | 根因 | 解法 |
|---|---|---|---|
| 7 | `UnknownHostException: postgres` | 宿主機跑後端卻用服務名；服務名只在 compose network 內可解析 | 容器內 `DB_HOST=postgres`，宿主 `localhost`，用 `${DB_HOST:localhost}` |
| 8 | 容器內 localhost 連不到 DB | 容器內 localhost 是自己 | compose 設 `DB_HOST: postgres` |
| 9 | `could not read from input file: Is a directory` | 掛載的宿主文件不存在，Docker 自動建了同名目錄 | 先 `Test-Path -PathType Leaf`；修正路徑後 `down -v` 重建 |
| 10 | 改了 schema.sql 沒生效 | init 腳本只在空 volume 跑一次 | 開發：`down -v` 重啟；**生產禁用**，用遷移工具 |
| 11 | backend 啟動時 DB connection refused | 裸 depends_on 不保證 DB 就緒 | healthcheck `pg_isready` + `condition: service_healthy` |
| 12 | PG 容器 Created 不啟動 | init 失敗 | `compose logs postgres` 查因，`down -v` 修復再起 |
| 13 | prod backend 連不到 Keycloak JWK | 跨 compose project 無法用服務名 | `host.docker.internal:9080` + `extra_hosts: host-gateway`；issuer 仍用 localhost 對外位址 |
| 14 | Hot reload 不生效 | 容器內文件事件不觸發 / Windows node_modules 污染 | `CHOKIDAR_USEPOLLING=true`；匿名卷 `/app/node_modules` |
| 15 | schema 路徑掛錯（`backend/resources/`） | 路徑拼寫錯 | 統一 `backend/src/main/resources/schema.sql`，掛載加 `:ro` |

### 4.3 認證與授權

| # | 症狀 | 根因 | 解法 |
|---|---|---|---|
| 16 | 未登入回 403 而非 401 | 無顯式 entryPoint，Spring 默認行為 | 顯式設 authenticationEntryPoint=401、accessDeniedHandler=403 |
| 17 | `WeakKeyException: 112 bits` | JWT HMAC key 需 ≥256 bits（32 bytes） | secret ≥ 32 bytes；生產用隨機長串 + Secret 管理 |
| 18 | token 解析失敗、時而 401 時而 403 | filter 靜默吞 RuntimeException；簽發/解析 secret 不一致；舊 token | 查 backend log；統一 JWT_SECRET；確認 Bearer 格式與 localStorage |
| 19 | `hasRole('ADMIN')` 不生效 | JWT 是 `ADMIN`，Spring 要 `ROLE_ADMIN` | filter / converter 統一補前綴 |
| 20 | USER 能 `GET /api/users` | 只給 DELETE 加了 ADMIN | `requestMatchers("/api/users/**").hasRole("ADMIN")` 護整個資源 |
| 21 | 前端隱藏菜單後仍可調 API | 前端不是安全邊界 | 後端強制授權；curl 驗證 |
| 22 | USER 碰 ADMIN API 被誤登出 | 攔截器見 403 也清 token | 只在 401 清態導頁，403 留頁提示 |
| 23 | 登出後直接輸入 URL 還能進 | 前端路由未守衛 / SSO session 未結束 | router guard + 清 localStorage；SSO 調 `keycloak.logout()` 結束會話 |
| 24 | admin/password 登入 401 | BCrypt hash 與密碼不符 / 舊 volume | 用正確 hash，`down -v` 重建 |
| 25 | 登入請求帶了過期舊 token / 回應字段讀錯 | 攔截器未排除 auth 端點；誤用 `data.role` | 排除 `/auth/`；用 `data.user.role`、`data.token` |

### 4.4 測試與 CI

| # | 症狀 | 根因 | 解法 |
|---|---|---|---|
| 26 | `Could not find a valid Docker environment` | Testcontainers 連不到 daemon/pipe | 確認 Docker Desktop；校正 `.testcontainers.properties` 的 pipe（`npipe:////./pipe/docker_engine`） |
| 27 | `client version 1.32 too old, min 1.40` | 舊 docker-java API | 升 Testcontainers 1.21.3；`.docker-java.properties` 設 api.version=1.40 |
| 28 | `relation "tasks" does not exist` | Testcontainers 非內嵌庫，schema.sql 不自動跑 | test profile：`spring.sql.init.mode: always` |
| 29 | 整合測試 expected 200 but was 401 | 沒帶 token | 先調 login API 取 token 帶 Bearer；**不要關 Security** |
| 30 | testCompile 失敗：找不到 AuthService/JwtUtil | 測試引用已廢棄類 | 測試跟隨當前職責重寫 |
| 31 | OWASP DC：NVD 403/404、NoDataException | 無/無效 API key、限流、proxy 阻擋 | `NVD_API_KEY` 環境變數 + nvdApiDelay + 重試上限；緩存 H2 |
| 32 | Gitleaks 在私有庫失敗 | gitleaks-action 私有庫需商業 license（工具本身 MIT 免費） | 公開庫無需 license；私有庫改用 Gitleaks CLI |
| 33 | GitHub 報 `No event triggers defined in 'on'` | 先 commit 了空文件，事後貼內容未再 add | push 前 `git status` + `git diff --cached` |

### 4.5 Git 歷史與敏感文件

- **誤提交後，刪文件沒用**：舊 commit 仍能查到。要徹底清除需 `git filter-repo`（官方推薦，替代 filter-branch）：
  ```bash
  git filter-repo --path backend/src/main/resources/application.properties --invert-paths --force
  ```
  - `--path` 必須是**相對於 repo 根目錄**的路徑（用 Windows 絕對路徑會等於沒刪）；
  - filter-repo 會自動移除 origin，需手動加回；再 `push --force --all`；
  - 通知所有協作者重新 clone；
  - **曾推送過的密鑰/密碼一律作廢重發**——重寫歷史改變不了「已被看到/爬走」的事實。
- `.gitignore` 只防「未來」，對已追蹤文件無效（本項目 `.env.prod` 已被追蹤，見第 5 章）。

### 4.6 排查方法論（高於具體坑）

1. **分層診斷**：先分環境問題 / 配置問題 / 業務邏輯問題，再動手。
2. **Stack trace 從最底層往上讀**；先確認「實際安裝版本」（lockfile、`npm ls`、dependency:tree），再懷疑代碼。
3. **看後端日誌，不要只看瀏覽器 console**。
4. 見到 `xxx is not a function` 且涉及 Node/內建模組，優先懷疑運行版本 < 依賴要求。
5. 提交前三件套：`git status` → `git diff --cached` → 確認沒有密鑰。

---

## 5. DevSecOps 專家評審

### 5.1 值得肯定的設計（亮點）✅

1. **Build Once, Deploy Many 落地完整**：鏡像不綁環境、Profile + 環境變數運行時切換、prod compose 不構建——這是很多團隊做不到的紀律。
2. **prod 下前後端都不碰密碼**：後端退為純 Resource Server，`AuthController` prod 直接 404，最小權限/最小攻擊面意識到位。
3. **縱深防禦**：路徑級 + 方法級授權、無狀態會話、顯式 401/403、非 root 運行、Actuator 只暴露 health、Postgres 不對外暴露端口。
4. **供應鏈安全覆蓋齊全**：Secret / SAST / SCA / Image 四類掃描都已接入，並採用「先觀察後收緊」的現實策略。
5. **測試用 Testcontainers 對齊生產數據庫**，且堅持在安全開啟狀態下測真實登入鏈路。
6. **文檔化文化極好**：每個 Lab 有筆記、每個坑有記錄，知識沉澱是 DevSecOps 能推進的前提。

### 5.2 必須修復的問題（按風險排序）🔴

| 優先級 | 問題 | 風險 | 建議 |
|---|---|---|---|
| P0 | **鏡像存在大量已知漏洞**：Trivy 報告 10 CRITICAL / 41 HIGH（Alpine OS 包 34 個、jar 89 個），含舊版 log4j 1.x（CVE-2023-6378）等 | 已知可利用漏洞直接暴露 | 升級基礎鏡像與依賴；Trivy 門禁從觀察改為 HIGH 即失敗；SBOM 常規化 |
| P0 | **`.env.prod` 已被 Git 追蹤**（.gitignore 只忽略 `.env`，漏了 `.env.prod`） | 生產配置模板/結構進版本庫，易誤填真值提交 | `git rm --cached .env.prod`；改為 `.env.prod.example`；補 `.env.*` 規則；掃描歷史確認無真值 |
| P1 | **安全門禁全部不阻斷**（OWASP DC / npm audit / Trivy 均 continue-on-error、exit-code 0） | 掃描流於形式，漏洞可進生產 | 設過渡時間表：先 CRITICAL 阻斷 → 再 HIGH；以 GitHub Branch Protection 要求 status check 通過 |
| P1 | **`security.yml`、`owasp-suppressions.xml` 是空文件**；docker-build job 不依賴 security-scan | 安全 job 失敗仍出鏡像；空文件造成誤解 | 補全或刪除；讓 docker-build/deploy `needs` 安全 job |
| P1 | **任務無物件級授權（IDOR）**：TaskController 任何登入者可讀寫刪所有任務，未校驗 assignee/createdBy | 水平越權 | Service 層按當前使用者過濾；`@PreAuthorize` + 所有權校驗；管理員豁免 |
| P2 | **直接綁定實體接收請求**（`@RequestBody Task`），無 DTO 分層 | Mass Assignment、過度暴露字段 | 引入 request DTO + Bean Validation（`@Valid`、大小寫/長度限制） |
| P2 | 代碼風險寫法：`@Autowired` 字段注入；存在 `src/test/.../unit/TaskService.java`（疑似複製錯誤的非測試類）；`frontend/api`、`frontend/router` 舊目錄殘留 | 可維護性、測試可移植性差 | 構造器注入；清理死代碼與冗餘目錄 |
| P2 | 默認弱口令（admin/password）、dev mock 任意密碼 | 若誤用到生產即災難 | 啟動強制改密；用環境開關/網路隔離確保 dev profile 不可能上生產 |
| P3 | JWT 無吊銷機制；local 模式登出後 token 在過期前仍有效 | 被竊 token 無法止損 | 短過期 + 刷新令牌；引入 jti 黑名單/版本聲明 |
| P3 | Nginx 缺安全響應頭（CSP、HSTS、X-Frame-Options 等） | XSS/點擊劫持 | 統一加安全頭；限制 `/api` 超時與 body 大小 |
| P3 | deploy 用 SSH + 根目錄 compose，主機漂移、無回滾 | 部署脆弱 | 容器編排平台化（見第 6 章） |

### 5.3 合規視角速評

- 對應 OWASP Top 10（2021）：A01 訪問控制（IDOR/物件級缺失）、A02 加密失敗（JWT 吊銷/默認密鑰）、A05 安全配置錯誤（安全頭、默認口令）、A06 脆弱組件（Trivy 高危）、A08 完整性（依賴/鏡像校驗）、A09 日誌（缺安全審計日誌）是主要缺口。
- 若面向客戶審計，需要補：SBOM 產出、掃描報告歸檔、修復 SLA、变更審批留痕（已有 Environment 審批雛形）。

---

## 6. 未來優化路線圖

### 6.1 短期（1–2 週，止血）

1. 修依賴/基礎鏡像，清空 CRITICAL/HIGH；Trivy、OWASP DC 改為 CRITICAL 阻斷。
2. `git rm --cached .env.prod`，補齊 `.gitignore`（`.env.*` 但保留 example），全歷史掃描確認無密鑰。
3. 補全/刪除空文件（security.yml、owasp-suppressions.xml），讓發版 job 依賴安全檢查。
4. 補任務物件級授權（IDOR）與 DTO + Validation。
5. Nginx 安全響應頭；actuator 收斂複核。
6. 默認口令隨機化 + 首次登入改密。

### 6.2 中期（1–2 月，工程化）

1. **門禁漸進收緊**：Branch Protection 強制 CI + 安全 check；HIGH 阻斷；誤報抑制走帶到期日的 allowlist（owasp-suppressions 規範化）。
2. **自動修復**：接入 Dependabot/Renovate 自動升級 PR；基礎鏡像固定 digest 並自動更新；SBOM（CycloneDX）隨構建產出歸檔。
3. **週期性重掃**：schedule workflow 每週重掃 main 與線上鏡像（新 CVE 每天出，不能只掃一次）。
4. **密鑰管理**：secret 集中管理（Secrets Manager / Vault / SOPS），CI 用 OIDC 短期憑證，撤離長期 SSH key；pre-commit 掃描（gitleaks hook）。
5. **可觀測性**：結構化日誌 + 審計日誌（登入、授權失敗、數據變更）+ 指標/告警；SAST 增量接 CodeQL/SonarQube Quality Gate。
6. **DAST**：測試環境部署後跑 OWASP ZAP baseline；前端補 E2E（Playwright）。
7. 數據庫遷移規範化：Flyway/Liquibase 取代「改 schema.sql + down -v」。

### 6.3 長期（季度級，平台化）

1. **從 Compose 走向編排平台**：Kubernetes（或 ECS/雲容器），配合 Helm/Kustomize；HPA、PodDisruptionBudget、NetworkPolicy、Pod Security Admission、非 root/只讀根文件系統。
2. **供應鏈安全（SLSA）**：provenance/attestation、鏡像簽名（Cosign）+ 入場校驗（Kyverno/OPA）、隔離構建、最小權限 CI。
3. **零信任架構**：服務間 mTLS（Service Mesh）、外部授權服務（OPA/Cedar PEP/PDP）、統一身份與工作負載身份。
4. **運行時防護**：Falco 運行時威脅檢測、容器漂移告警。
5. **策略即代碼與合規自動化**：CIS Benchmark 掃描、合規報表自動生成、修復 SLA 可視化。
6. 架構演進：模組拆分/API 版本化、快取與消息隊列、藍綠/金絲雀發布與自動回滾。

### 6.4 優先級矩陣

```text
高影響 × 低成本（立即做）          高影響 × 高成本（規劃做）
· 升依賴清漏洞                      · K8s 平台化
· .env.prod 脫鉤 + 門禁阻斷         · 鏡像簽名/SLSA
· IDOR 修復 + DTO                  · Vault/OIDC 零信任
低影響 × 低成本（順手做）          低影響 × 高成本（暫不做）
· 清死代碼/空文件                   ·（避免過度設計）
· Nginx 安全頭
```

---

## 7. 自測題

**簡答**

1. 為什麼同一個後端鏡像能支撐 dev/uat/prod 三種認證？說出兩個關鍵機制。
2. prod 下後端為什麼要把 issuer-uri 和 jwk-set-uri 配成不同位址？
3. 解釋「isSso 與 isAuthenticated 是正交維度」，並各舉一個 2×2 場景。
4. SPA 使用 Authorization Code + PKCE 而不用 Implicit 的理由？
5. 為什麼「改 schema.sql 後重啟」在開發環境可能無效？正確做法是什麼？生產環境又該怎麼做？
6. Trivy 報了 HIGH 就一定要停發版嗎？還需要判斷什麼？
7. 同事把數據庫密碼提交進了倉庫，寫出完整補救步驟（含 Git 歷史與密鑰本身）。

**實戰**

8. 給 TaskController 設計物件級授權方案，保證 USER 只能動自己相關任務、ADMIN 全開。
9. 改造 ci.yml：使任何 CRITICAL 漏洞或 SAST High 發現都會阻止鏡像發布，並允許帶到期日的誤報豁免。
10. 為前端 Nginx 寫一份含安全響應頭與 `/api` 代理的配置。

---

### 附：推薦延伸學習

- OWASP Top 10 / ASVS / Cheat Sheets；NIST SSDF、SLSA Framework
- Spring Security 官方：OAuth2 Resource Server / Method Security
- Keycloak 官方文檔：realm/client/role 模型、PKCE 參數
- Aqua Trivy、Semgrep Rules、Gitleaks 配置；OWASP ZAP、Falco、Cosign/Sigstore
- 書目/資源：《Securing DevOps》、Google SRE Security、CNCF Security TAG

> 一句話總結本項目：**它把「身份能力外置（Keycloak）+ 環境差異運行時注入（Profile）+ 安全掃描全鏈路左移」三件事做對了雛形；下一步的核心是讓安全門禁真正「咬下去」、補上物件級授權，並把供應鏈與運行時安全平台化。**
