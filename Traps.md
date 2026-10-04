# Build Once / SSO 改造踩坑紀錄 🚧💥

本文記錄把認證改造成「dev = Mock、uat = DB、prod = Keycloak SSO」並落實 **Build Once, Deploy Many** 過程中踩到的六個坑。每個坑附上症狀、根因、解法與教訓。🕵️

---

## 坑 1：舊的 `SecurityConfig.class` 殘留，Bean 重複註冊 🧟

### 症狀 😵

dev 環境啟動直接失敗：

```text
The bean 'securityFilterChain', defined in .../config/SecurityConfig.class,
could not be registered. A bean with that name has already been defined in
.../config/DefaultSecurityConfig.class and overriding is disabled.
```

但原始碼裡 `SecurityConfig.java` 早就刪掉了。👻

### 根因 🧠

Maven **增量編譯不會自動清除「原始碼已刪除、class 還在」的舊產物**。`docker-compose.dev.yml` 又掛載了整個 `./backend`（含 `target/`），於是：

```text
target/classes/.../SecurityConfig.class      （舊屍體）
target/classes/.../DefaultSecurityConfig.class（新類別）
                └─ 兩個都定義 securityFilterChain → 衝突 💥
```

### 解法 🛠️

```powershell
Remove-Item -Recurse -Force backend\target
```

並把 dev compose 的啟動指令改成**每次先 clean**：

```yaml
command: mvn clean spring-boot:run ...
```

### 教訓 💡

> 刪掉 `.java` 不等於刪掉對應的 `.class`。掛載原始碼的開發容器，啟動前加一道 `clean` 最保險。

---

## 坑 2：dev 關閉 MyBatis，`TaskMapper` Bean 找不到 🔌

### 症狀 😵

```text
Field taskMapper in ...service.TaskService required a bean of type
'...mapper.TaskMapper' that could not be found.
```

### 根因 🧠

為了「Mock 登入不碰資料庫」，在 `application-dev.yml` 排除了資料來源與 MyBatis 自動配置：

```yaml
spring.autoconfigure.exclude:
  - DataSourceAutoConfiguration
  - MybatisPlusAutoConfiguration
```

但**認證方式與資料庫是兩回事**——任務模組（`TaskService`）啟動時仍要注入 `TaskMapper`，MyBatis 一關，Mapper 無人註冊。

### 解法 🛠️

移除排除項，dev 正常使用 PostgreSQL（dev compose 本來就啟動了 postgres），**只有「登入」這一步被 mock**：

```yaml
# application-dev.yml：登入由 @Profile("dev") 的 MockAuthenticationService 處理
# 資料來源 / MyBatis 保持預設，任務模組正常運作
```

### 教訓 💡

> 想簡化的是「登入」，不是讓整支應用沒有資料庫。把「認證策略」和「基礎設施依賴」分開看。

---

## 坑 3：prod compose `depends_on` 一個不存在的服務 🔗

### 症狀 😵

```text
service "backend" depends on undefined service "keycloak": invalid compose project
```

### 根因 🧠

Keycloak 定義在獨立的 `docker-compose.keycloak.yml`，prod compose 卻寫了：

```yaml
depends_on:
  keycloak: { condition: service_started }
```

分開執行時，這個 compose project 裡根本沒有 `keycloak` 服務。更隱蔽的是：兩個 compose 是**獨立 project、不同 network**，就算不報錯，backend 也無法用服務名 `http://keycloak:8080` 解析到對方。

### 解法 🛠️

1. 移除 `depends_on: keycloak`（改成手動確保啟動順序：先 Keycloak 後 prod）
2. backend 經**宿主機映射埠**抓 JWK：

```yaml
environment:
  KEYCLOAK_JWK_SET_URI: http://host.docker.internal:9080/realms/taskapp/protocol/openid-connect/certs
extra_hosts:
  - "host.docker.internal:host-gateway"
```

### 教訓 💡

> `depends_on` 只在「同一個 compose project」內有效。跨 project 的容器溝通，要走宿主機埠或共用 external network。

---

## 坑 4：跑的是舊鏡像，難怪沒有 SSO 🕰️

### 症狀 😵

`http://localhost` 打開後仍是舊的帳密登入頁，完全不跳 Keycloak。

### 根因 🧠

查鏡像建置時間發現：

```text
fantasy15/task-app-frontend:v1   created=2026-09-26   ← SSO 代碼之前
fantasy15/task-app-backend:v1    created=2026-09-25   ← SSO 代碼之前
```

prod compose 只寫 `image:`、沒寫 `build:`，`up` 不會自動重建 → 一直在跑 SSO 功能出現前的舊鏡像（backend 沒有 actuator、沒有 `/api/auth/config`，也解釋了為什麼一直 unhealthy）。

### 解法 🛠️

構建一個**新版本 v2**（不覆蓋 v1），再用 v2 部署：

```powershell
docker build -t fantasy15/task-app-backend:v2  ./backend
docker build -t fantasy15/task-app-frontend:v2 ./frontend
# .env：IMAGE_TAG=v2
docker compose -f docker-compose.prod.yml up -d
```

### 教訓 💡

> 「代碼改了、容器也重啟了」不等於「鏡像更新了」。`up` 預設只會用既有鏡像。行為怪異時先看 `docker images` 的建置時間。

---

## 坑 5：在 prod compose 加 `build:`，反而破壞 Build Once 🏗️

### 症狀 😵

為了方便，一度在 `docker-compose.prod.yml` 加了：

```yaml
build: { context: ./backend }
```

### 根因 🧠

這等於讓「**部署**」順便「**構建**」：每個環境可能各自構建，prod 與 uat 不再保證是同一份製品 —— 直接違背 Build Once。

### 解法 🛠️

把構建與部署**職責分離**：

| 階段 | 工具 | 動作 |
|---|---|---|
| Build（一次） | `build-images.ps1` | 構建 backend + frontend，打同一個 tag，不繫結環境 |
| Deploy（多次） | compose / `deploy.ps1` | 只引用鏡像 tag，**沒有 `build:`** |

```powershell
# 構建一次
.\build-images.ps1
# UAT 與 prod 都用同一個 tag
$env:IMAGE_TAG="v2"; docker compose up -d
$env:IMAGE_TAG="v2"; docker compose -f docker-compose.prod.yml up -d
```

### 教訓 💡

> Build Once 的重點是「**不可變製品只產生一次，然後被各環境引用**」。部署描述檔不該承擔構建職責。

---

## 坑 6：無 BOM 的 `.ps1` 在 PowerShell 5.1 中文爆雷 🔌

### 症狀 😵

用 Windows PowerShell 執行腳本：

```text
The string is missing the terminator: ".
```

但腳本內容看起來引號完全正常。

### 根因 🧠

- 檔案是**無 BOM 的 UTF-8**
- **Windows PowerShell 5.1** 對無 BOM 的 `.ps1` 預設用系統 ANSI 字碼頁（Big5/950）解碼，而非 UTF-8
- 中文註解「範**例**」的某個 UTF-8 位元組在 Big5 錯位解碼後恰好變成 `0x22`（`"`）→ 憑空多出一個引號，字串到檔尾都沒閉合
- `pwsh`（PowerShell 7）預設 UTF-8，所以不會發生

### 解法 🛠️

把含中文的 `.ps1` 存成 **UTF-8 with BOM**，5.1 與 7 都能正確識別，並用 5.1 解析器回歸驗證：

```powershell
$text = [System.IO.File]::ReadAllText("build-images.ps1")
[System.IO.File]::WriteAllText("build-images.ps1", $text, (New-Object System.Text.UTF8Encoding($true)))
```

### 教訓 💡

> Windows 上含中文的 `.ps1`，一律存「UTF-8 with BOM」。遇到「語法明明對卻說引號沒閉合」，先懷疑編碼。

---

## 經驗總結（一句話各坑）📝

1. 🧟 **舊 class 坑**：刪原始碼不會清 class，開發容器啟動前先 `clean`。
2. 🔌 **過度排除坑**：Mock 的是登入，不要連任務模組需要的資料庫一起關掉。
3. 🔗 **跨 compose 坑**：`depends_on` 與服務名解析只在同一 project 內成立。
4. 🕰️ **舊鏡像坑**：`up` 不會重建鏡像，行為怪異先查鏡像建置時間。
5. 🏗️ **職責分離坑**：構建只做一次，部署只引用不可變鏡像。
6. 🔌 **編碼坑**：含中文的 Windows `.ps1` 用 UTF-8 with BOM。

## 驗證清單 ✅

- [ ] dev 啟動前會 `mvn clean`，無殘留舊 class
- [ ] dev 可 mock 登入（密碼任意），任務 API 仍可正常讀寫 PostgreSQL
- [ ] prod compose 不依賴外部 compose 的服務名，JWK 走 `host.docker.internal:9080`
- [ ] 已構建含 SSO 代碼的新鏡像（v2），`docker images` 時間為最新
- [ ] prod compose 沒有 `build:`，只引用鏡像 tag
- [ ] `http://localhost` 會跳轉 Keycloak 登入頁
- [ ] 含中文的 `.ps1` 均為 UTF-8 with BOM，5.1 可正常執行
