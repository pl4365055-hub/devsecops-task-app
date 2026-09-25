# Lab 10 - CI Pipeline with GitHub Actions 🚀

## 1. 學習目標 🎯

- 認識 CI（Continuous Integration，持續整合）的核心概念與價值
- 使用 GitHub Actions 建立自動化 CI Pipeline
- 在 Pipeline 中自動執行 frontend（Vue/Vitest）與 backend（Spring Boot/Maven）測試
- 整合 SAST 靜態掃描（Gitleaks、Semgrep）
- 自動建置 Docker Image 並用 Trivy 掃描弱點
- 理解 workflow 觸發條件（`on`）、jobs、steps 與依賴關係（`needs`）
- 避開「空檔案被 commit」與「Node 版本 / jsdom 不相容」等常見踩坑 🚧

> 💡 踩坑細節請另見 [Traps_lab10.md](Traps_lab10.md)，本文聚焦「怎麼做」與「為什麼」。

---

## 2. 什麼是 CI？🤔

CI（持續整合）是指開發者把程式碼頻繁地合併（merge）到主分支，**每次 push / PR 都由自動化流程驗證**：

```text
開發者 push code
      ↓
  🔻 自動觸發 CI
      ↓
 🧪 安裝依賴 → 跑測試 → Lint
      ↓
 🔍 安全掃描（SAST / Secret）
      ↓
 🐳 建置 Docker Image → 弱點掃描
      ↓
 🟢 全綠 = 可放心合併
 🔴 有紅 = 立即修復
```

CI 的好處：

- ✅ 早點發現 bug，不要等到上線才炸
- ✅ 每個人的程式碼都用「同一套乾淨環境」驗證（在我電腦有好 🤥 → 不再成立）
- ✅ 把重複的人工檢查自動化
- ✅ 為後續的 CD（持續部署／交付）打底

---

## 3. GitHub Actions 基本概念 🧩

| 名詞 | 說明 |
| --- | --- |
| **Workflow** | 一個自動化流程，定義在 `.github/workflows/*.yml` |
| **Event（`on`）** | 觸發條件，例如 `push`、`pull_request` |
| **Job** | 一組在同一個 runner 上執行的 steps，預設可平行執行 |
| **Step** | Job 內的一個個動作，可以是 shell 指令或 Action |
| **Action** | 可重用的現成步驟，例如 `actions/checkout` |
| **Runner** | 實際執行的虛擬機器，本 Lab 用 `ubuntu-latest` 🐧 |
| **`needs`** | 宣告 Job 間依賴，被依賴的 Job 成功後才執行 |

檔案位置：

```text
.github/
└── workflows/
    └── ci.yml    ← 本次的 CI Pipeline
```

---

## 4. Pipeline 整體架構 🏗️

本 Lab 的 Pipeline 包含 4 個 Job：

```text
                    ┌──────────────────────┐
                    │  on: push / PR       │  ⚡ 觸發
                    └──────────┬───────────┘
               ┌──────────────┴──────────────┐
               ▼                             ▼
     ┌──────────────────┐          ┌──────────────────┐
     │  frontend-test   │          │   backend-test   │   🧪 平行跑
     └────────┬─────────┘          └────────┬─────────┘
              └──────────────┬─────────────┘
                  ┌──────────┴──────────┐
                  ▼                     ▼
        ┌──────────────────┐  ┌──────────────────┐
        │  security-scan   │  │   docker-build   │   🔍🐳
        └──────────────────┘  └──────────────────┘
```

- `frontend-test`、`backend-test`：沒有相依，**平行執行**省時間 ⏱️
- `security-scan`、`docker-build`：透過 `needs` 等兩個測試 Job 都成功才執行

---

## 5. 觸發條件 `on` ⚡

```yaml
name: CI Pipeline

on:
  push:
    branches: [main, develop]
    paths-ignore:
      - '**/*.md'
  pull_request:
    branches: [main]
    paths-ignore:
      - '**/*.md'
```

說明：

- 🔸 `push`：程式推到 `main` 或 `develop` 時觸發
- 🔸 `pull_request`：對 `main` 發 PR 時觸發
- 🔸 `paths-ignore: '**/*.md'`：只改 Markdown 文件時不跑 Pipeline（省額度、省時間）💸

> ⚠️ **踩坑提醒**：`on:` 是 workflow 一定要有的區塊。若檔案是空的（0 bytes），GitHub 會報
> `No event triggers defined in 'on'`。詳見 [Traps_lab10.md](Traps_lab10.md)。

---

## 6. Job 1：Frontend Test 🎨

在 `frontend/` 目錄（Vue + Vite）執行：

```yaml
  frontend-test:
    name: Frontend Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'
          cache-dependency-path: frontend/package-lock.json

      - name: Install dependencies
        working-directory: frontend
        run: npm ci

      - name: Lint
        working-directory: frontend
        run: npm run lint --if-present

      - name: Run tests
        working-directory: frontend
        run: npx vitest run

      - name: Dependency Audit
        working-directory: frontend
        run: npm audit --audit-level=high
        continue-on-error: true   # 先警告，之後可改為失敗

      - name: Build
        working-directory: frontend
        run: npm run build
```

重點解析：

- 📥 `actions/checkout@v4`：把 repository 原始碼拉下來
- 🟢 `actions/setup-node@v4`：安裝 Node.js 並快取 npm 依賴
- 📦 `npm ci`：依 `package-lock.json` **乾淨安裝**（CI 專用，比 `npm install` 更可重現）
- 🧪 `npx vitest run`：執行 Lab 07 的 Vitest 測試（見 `src/__tests__/Login.spec.js`）
- 🛡️ `npm audit`：檢查 npm 依賴已知弱點；`continue-on-error: true` 表示觀察期先不讓 Pipeline 失敗
- 🏗️ `npm run build`：確認正式版可建置

> ⚠️ **版本相容踩坑**：本 Pipeline 使用 Node 20，因此 `jsdom` 必須釘在 **v26**（v30 依賴的 undici 8 需要 Node 22.19+，會讓 Vitest worker 崩潰報 `webidl.util.markAsUncloneable is not a function`）。完整排查見 [Traps_lab10.md](Traps_lab10.md)。

---

## 7. Job 2：Backend Test ☕

在 `backend/` 目錄（Spring Boot + Maven）執行：

```yaml
  backend-test:
    name: Backend Test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: 'maven'

      - name: Run tests
        working-directory: backend
        run: mvn test

      #- name: OWASP Dependency Check
      #  working-directory: backend
      #  run: mvn org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7
      #  continue-on-error: true

      - name: Package
        working-directory: backend
        run: mvn package -DskipTests
```

重點解析：

- ☕ `actions/setup-java@v4`：安裝 **Temurin JDK 17** 並快取 Maven 依賴
- 🧪 `mvn test`：執行 JUnit 5 單元測試（Lab 07）
- 📦 `mvn package -DskipTests`：打包成可部署的 JAR（前面已跑過測試，這裡跳過）

> 📝 OWASP Dependency-Check 目前先註解（NVD API key / rate limit 問題在 Lab 08 踩過，見 [Traps_lab08.md](Traps_lab08.md)）。等設定好 `NVD_API_KEY` secret 後可重新啟用。

> 💡 注意：CI runner 上的 `mvn` 是 GitHub runner 內建的，與本機 `backend/.tools` / `mvn-local.ps1` 是兩回事，不要混用。

---

## 8. Job 3：Security Scan（SAST）🔍

測試通過後才執行祕密與靜態安全掃描：

```yaml
  security-scan:
    name: Security Scan
    runs-on: ubuntu-latest
    needs: [frontend-test, backend-test]
    steps:
      - uses: actions/checkout@v4

      - name: Run Gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITLEAKS_LICENSE: ${{ secrets.GITLEAKS_LICENSE }}

      - name: Semgrep SAST
        uses: semgrep/semgrep-action@v1
        with:
          config: >-
            p/owasp-top-ten
            p/java-spring
            p/javascript
```

工具說明：

| 工具 | 掃描目標 |
| --- | --- |
| 🔑 **Gitleaks** | 偵測程式碼 / git 歷史中誤 commit 的 API key、密碼、token 等祕密；部分版本需要 `GITLEAKS_LICENSE`（存於 repository Secrets，**不要寫死**） |
| 🩺 **Semgrep SAST** | 靜態分析原始碼弱點，載入 OWASP Top 10、Java Spring、JavaScript 規則集 |

> 🔐 `${{ secrets.XXX }}` 會在執行時注入，值不會出現在 log 與程式碼中。

---

## 9. Job 4：Docker Build & Scan 🐳

自動建置 frontend / backend 的 Docker Image，再用 Trivy 掃描：

```yaml
  docker-build:
    name: Docker Build & Scan
    runs-on: ubuntu-latest
    needs: [frontend-test, backend-test]
    steps:
      - uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build backend image
        uses: docker/build-push-action@v5
        with:
          context: ./backend
          push: false
          tags: backend:ci-${{ github.sha }}
          load: true

      - name: Build frontend image
        uses: docker/build-push-action@v5
        with:
          context: ./frontend
          push: false
          tags: frontend:ci-${{ github.sha }}
          load: true

      - name: Trivy scan backend
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: backend:ci-${{ github.sha }}
          format: 'table'
          severity: 'CRITICAL,HIGH'
          exit-code: '0'      # 觀察階段先不失敗

      - name: Trivy scan frontend
        uses: aquasecurity/trivy-action@master
        with:
          image-ref: frontend:ci-${{ github.sha }}
          format: 'table'
          severity: 'CRITICAL,HIGH'
```

重點解析：

- 🔧 `docker/setup-buildx-action`：啟用 Buildx（更強的建置後端）
- 🏗️ `docker/build-push-action`：依 `backend/`、`frontend/` 各自的 Dockerfile 建置
  - `push: false` + `load: true`：只建到本機 runner，不推到 registry
  - tag 使用 `${{ github.sha }}`，每次提交都有唯一可追溯的版本 🧾
- 🧫 **Trivy**：掃描 Image 作業系統與函式庫弱點，目前只看 `CRITICAL,HIGH`

> 🔧 觀察期把 `exit-code` 設為 `'0'`（有弱點也不讓 Pipeline 失敗）；穩定後建議改成 `'1'`，讓高危弱點確實擋下不合適的程式碼。🚦

---

## 10. 完整執行流程（生命週期）🔄

```text
git push origin main
        ↓
GitHub 收到 push event（on.push 符合 main，且非只改 .md）
        ↓
開一台 ubuntu-latest runner（每個 Job 各自獨立）
        ↓
 ┌──────────────┬───────────────┐
 ▼              ▼
frontend-test  backend-test     （平行）
 npm ci          setup JDK 17
 vitest run      mvn test
 npm audit       mvn package
 npm run build
 └──────┬────────┴───────┘
        ▼ （兩個都成功）
 ┌──────────────┬───────────────┐
 ▼              ▼
security-scan  docker-build
 Gitleaks       Buildx 建置 ×2
 Semgrep        Trivy 掃描 ×2
        ↓
   🟢 全部成功 → 程式碼通過 CI 驗證
```

---

## 11. 本機開發與除錯 🧑‍💻

推上去之前，建議先在本機把對應指令跑過：

```powershell
# Frontend
cd frontend
npm ci
npx vitest run
npm run build
```

```powershell
# Backend（使用專案本地 Maven）
cd backend
.\mvn-local.ps1 test
.\mvn-local.ps1 package -DskipTests
```

驗證 YAML 語法（commit 前避免再踩空檔案 / 縮排坑）：

- 🖱️ VS Code 安裝 **GitHub Actions** 延伸模組，會即時標示語法錯誤
- 👀 提交前確認：

```powershell
git status
git diff --cached
```

> 📌 `git add` 只快照「當下」內容：先 add 再貼內容，等於沒 add。每次編輯後要重新 add 才會進 commit。

---

## 12. 常見問題 FAQ ❓

**Q1：為什麼 Actions 頁面出現 `No event triggers defined in 'on'`？** 📭
A：九成是 push 上去的 `ci.yml` 是空檔案或 `on:` 區塊有誤。用 `git show HEAD:.github/workflows/ci.yml` 確認提交內容。

**Q2：為什麼 `npm ci` 沒事，跑 `vitest` 才炸 `markAsUncloneable`？** 💣
A：npm 對 engines 不符只發警告，不會中斷安裝；問題在實際執行才發生。Node 20 請搭配 jsdom v26，或把 Node 升到 22。

**Q3：為什麼改 `.md` 文件沒有觸發 CI？** 📄
A：因為設了 `paths-ignore: '**/*.md'`，這是刻意省資源的設計；若要強制觸發可同時改一個非 `.md` 檔，或暫時移除該設定。

**Q4：`continue-on-error: true` 是什麼？** 🟡
A：該步驟即使失敗，整個 Job 仍視為成功。適合「觀察期」工具上線，之後應移除讓問題真正被擋下。

**Q5：Secrets（如 `GITLEAKS_LICENSE`）要在哪設定？** 🔐
A：GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**。

---

## 13. 完成檢查表 ✅

- [ ] `.github/workflows/ci.yml` 存在且非空
- [ ] `on:` 定義了 `push` / `pull_request` 觸發條件
- [ ] `frontend-test` Job 使用 Node 20 + `npm ci` + `vitest run`
- [ ] jsdom 版本與 Node 版本相容（Node 20 → jsdom v26）
- [ ] `backend-test` Job 使用 JDK 17 + `mvn test`
- [ ] `security-scan` 透過 `needs` 在測試成功後執行（Gitleaks / Semgrep）
- [ ] `docker-build` 可建置兩個 Image 並執行 Trivy 掃描
- [ ] 必要的 Secrets 已在 repository 設定
- [ ] push 後 GitHub Actions 四個 Job（或啟用中的 Job）皆為綠燈 🟢
- [ ] 已閱讀 [Traps_lab10.md](Traps_lab10.md) 了解踩坑細節 📖

---

## 14. 相關文件 🔗

- [Lab07_AutoTest.md](Lab07_AutoTest.md)：Backend 自動化測試（Maven / Testcontainers）
- [Lab07_FrontendAutoTest.md](Lab07_FrontendAutoTest.md)：Frontend 自動化測試（Vitest / jsdom）
- [Traps_lab08.md](Traps_lab08.md)：OWASP Dependency-Check / NVD API 踩坑
- [Traps_lab10.md](Traps_lab10.md)：本 Lab 的 CI 踩坑總結 🚧
