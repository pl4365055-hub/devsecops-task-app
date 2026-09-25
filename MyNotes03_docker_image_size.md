# 🐳 Dockerfile × docker-compose 關係，與多階段建構的映像瘦身

兩件相關的事：一是 Dockerfile 和 compose 到底誰負責什麼；二是多階段建構為什麼能讓映像從 700+ MB 縮到 300 MB 以下。

## 🔤 名詞中英對照

- Dockerfile：映像建構腳本（image 的食譜）
- docker-compose.yml：多容器編排檔（容器怎麼一起跑）
- Multi-stage build：多階段建構
- Build context：建構內容目錄（compose 裡 `build: ./backend` 指的那個目錄）
- Base image：基底映像（`FROM` 後面那個）

---

## 筆記一：Dockerfile 與 docker-compose 的分工

### 🧠 第一性原理

image 是「靜態的檔案」，容器是「跑起來的行程」。兩者需要的資訊本來就不同，所以分成兩個檔案。

### 📖 What：各自負責什麼

| 檔案 | 職責 | 內容範例 |
|---|---|---|
| `backend/Dockerfile`、`frontend/Dockerfile` | **怎麼建構 image** | base image、COPY 什麼、RUN 什麼指令、多階段、ENTRYPOINT |
| `docker-compose.yml` | **怎麼執行容器** | 服務網路、port 對應、environment、volumes、depends_on、健康檢查 |

compose 裡的 `build: ./backend` 只是一個**指標**：告訴 Docker「到 `./backend` 目錄找 `Dockerfile` 來建」。它本身**不含**任何建構步驟。

`docker compose up --build` 底層等價於：

```
docker build -t <自動命名> ./backend    # 用 backend/Dockerfile
docker build -t <自動命名> ./frontend   # 用 frontend/Dockerfile
docker run ...                           # 再照 compose 設定啟動
```

刪掉 Dockerfile，`build:` 就直接報錯；compose 無法自己建出 image。

### 🔀 對照：dev compose 連 build 都不做

`docker-compose.dev.yml` 沒有 `build`，而是直接用官方映像 + bind mount 原始碼：

- **生產 compose**：用 Dockerfile 多階段建構，映像精簡、安全
- **開發 compose**：`image: maven:...` / `image: node:20-alpine` + `volumes: ./backend:/app`，容器內即時編譯，支援 hot reload

### ✅ 一句話

> Dockerfile 決定 **image 裡有什麼**；compose 決定**容器怎麼一起跑**。`build` 鍵只是引用 Dockerfile 的捷徑，不取代它。

---

## 筆記二：多階段建構為什麼讓映像變小

### 📏 實測觀察

- backend 最終映像 `devsecops-task-app-backend`：**300+ MB**（maven 建構映像 700+ MB）
- frontend 最終映像 `devsecops-task-app-frontend`：**90+ MB**（node 映像 190+ MB）

> 映像名稱是 Compose 自動用「資料夾名稱-service 名稱」組出來的。

### 🧠 原理：編譯工具不進入最終映像

```
backend/Dockerfile
階段1 (build):  maven:3.9-eclipse-temurin-17   ← JDK + Maven + .m2 依賴（大）
                 ↓ COPY --from=build 只拿一個 jar
階段2 (final):  eclipse-temurin:17-jre-alpine  ← 只有 JRE，沒有 JDK/Maven/原始碼

frontend/Dockerfile
階段1 (build):  node:20-alpine                 ← Node + node_modules（大）
                 ↓ COPY --from=build 只拿 dist/ 靜態檔
階段2 (final):  nginx:alpine                   ← 只有 nginx，連 Node 都沒有
```

| | Build stage 有、但 final 沒有 | Final image 留下的 |
|---|---|---|
| backend | JDK、Maven、`~/.m2` 依賴、原始碼 | JRE + 一個 `.jar` |
| frontend | Node runtime、`node_modules`、原始碼 | nginx + `dist/` 靜態檔 |

### 💥 So what：不只是省空間

- 映像裡沒有編譯器、沒有原始碼、沒有多餘工具 → **攻擊面更小**
- Trivy 映像掃描的漏洞數會更少（可比對 `trivy-backend-report.json`）
- 拉取、部署都更快

### ⚠️ 注意：本機 build cache 仍然佔硬碟

多階段只讓「最終映像」不含 build stage，但 Docker 為了 cache 會把中間映像留在本機（`docker images` 裡的 `<none>` dangling images）：

- 最終映像小 → 拉取、部署、掃描都快 ✅
- 本機 cache 仍佔空間 → 用 `docker image prune` / `docker builder prune` 清理

### 🔎 驗證指令

```powershell
docker images                                         # 看所有映像大小
docker history devsecops-task-app-backend             # 每一 layer 佔多少
docker history devsecops-task-app-frontend
docker buildx du                                      # build cache 佔用
```

`docker history` 會直接顯示：backend final image 沒有 maven 那幾層、frontend final image 沒有 node 那幾層——那幾百 MB 就是這樣被「丟掉」的。

### ✅ 一句話

> 多階段建構 = 在「大映像」裡編譯，只把成品複製到「小映像」裡執行；工具鏈與原始碼永遠不進入最終映像。
