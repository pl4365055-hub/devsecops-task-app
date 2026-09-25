# Lab 10 - GitHub Actions CI 踩坑紀錄 🚧💥

本文記錄在建立 `.github/workflows/ci.yml` CI Pipeline 過程中遇到的三個坑：空檔案被推上去、`on` 觸發事件錯誤、以及 Node 20 與新版 jsdom/undici 不相容導致 Vitest worker 崩潰。每個坑都附上症狀、排查過程、根因與解法。🕵️

---

## 坑 1：`git push` 後 GitHub Actions 報錯 `No event triggers defined in 'on'` 📭

### 症狀 😵

`ci.yml` 本機看起來內容完整（有 `on:`、有 `jobs:`），但 push 到 GitHub 後，Actions 頁面出現：

```text
Error
No event triggers defined in `on`
```

Workflow 完全不會被觸發。

### 排查過程 🔍

1. 本機打開 `ci.yml`，`on:` 區塊明明正常：

   ```yaml
   on:
     push:
       branches: [main, develop]
     pull_request:
       branches: [main]
   ```

2. 檢查檔案原始 bytes，沒有 BOM、沒有奇怪字元。✅

3. 執行 `git status`，發現 `ci.yml` 是 **modified（未提交）** 狀態：⚠️

   ```text
   Changes not staged for commit:
       modified:   .github/workflows/ci.yml
   ```

4. 執行 `git diff`，看到致命線索：

   ```diff
   index e69de29..a321c30 100644
   --- a/.github/workflows/ci.yml
   +++ b/.github/workflows/ci.yml
   @@ -0,0 +1,129 @@
   +name: CI Pipeline
   ...
   ```

   - blob hash **`e69de29`** 正是 git 中「**空檔案（0 bytes）**」的標誌性 hash
   - 129 行內容全部標記為 `+`，代表它們只存在於工作目錄，從未被 commit

5. 用 `git show HEAD:.github/workflows/ci.yml` 驗證，**完全沒有輸出**——確認 commit 裡的檔案是空的。🫥

### 根因 🧠

提交（commit `chore: add ci pipeline`）發生時，`ci.yml` 還是空檔案；之後才把內容貼進去，但**從未再次 `git add` + `git commit`**。所以：

- 本機看到的：完整內容 👀
- GitHub 收到的：空檔案 📭
- GitHub 解析空 YAML，自然找不到 `on`，於是報 `No event triggers defined in 'on'`

### 解法 🛠️

```powershell
git add .github/workflows/ci.yml
git commit -m "ci: populate workflow triggers and jobs"
git push
```

### 教訓 💡

> **`git add` 只快照「當下」的內容。** 先 add 空檔案、再編輯，等於白編——後續的修改不會自動進入 commit。
> push 前養成好習慣：`git status` + `git diff --cached` 看清楚到底提交了什麼。👀

---

## 坑 2：Vitest worker 啟動失敗，`webidl.util.markAsUncloneable is not a function` 💣

### 症狀 😵

`ci.yml` 補上內容重新 push 後，Frontend Test job 在 `npx vitest run` 步驟直接掛掉：

```text
⎯⎯⎯⎯⎯⎯ Unhandled Error ⎯⎯⎯⎯⎯⎯⎯
Error: [vitest-pool]: Failed to start forks worker for test files
.../frontend/src/__tests__/Login.spec.js.

Caused by: TypeError: webidl.util.markAsUncloneable is not a function
 ❯ new CacheStorage node_modules/undici/lib/web/cache/cachestorage.js:20:17
 ❯ Object.<anonymous> node_modules/undici/index.js:179:25
 ❯ Object.<anonymous> node_modules/jsdom/lib/api.js:12:33

 Test Files  no tests
      Tests  no tests
     Errors  1 error
Error: Process completed with exit code 1.
```

注意 `no tests`——**測試本身根本沒機會執行**，是 worker 啟動階段就崩了。🏚️

### 排查過程 🔍

1. 從 stack trace 由下往上讀：`jsdom` → `undici` → `CacheStorage` → `webidl.util.markAsUncloneable`。📜

2. 檢查 `frontend/package.json` 與 lockfile 中實際安裝的版本：

   | 套件 | 版本 | engines 要求的 Node |
   | --- | --- | --- |
   | `jsdom` | 30.1.0 | `^22.22.2 \|\| ^24.15.0 \|\| >=26.0.0` ❌ |
   | `undici`（jsdom 的依賴） | 8.10.2 | `>=22.19.0` ❌ |
   | `vite` | 8.3.0 | `^20.19.0 \|\| >=22.12.0` ⚠️ |
   | CI 指定 | — | **Node 20** ❌ |

3. 比對 Node 版本支援：`markAsUncloneable` 這個 WebIDL API 是 **Node 22.19+ 才加入**的，Node 20 的內建實作裡沒有，所以一 call 就 `TypeError`。💥

4. 疑點：為什麼 `npm ci` 安裝時沒有失敗？因為 **npm 預設對 engines 不符只發 warning，不會中斷安裝**。錯誤被延後到「實際執行」才引爆。🧨

### 根因 🧠

依賴版本與執行環境錯配（dependency/runtime mismatch）：

```text
Node 20（CI 指定）
   └─ jsdom 30
       └─ undici 8
           └─ 需要 Node 22.19+ 才有的 webidl.util.markAsUncloneable
               └─ 💥 TypeError：worker 無法啟動
```

這套最新工具鏈（Vite 8 / Vitest 5 / jsdom 30）全部是朝 **Node 22+** 看齊釋出的，Node 20 已經被新一代 jsdom 拋下。

### 兩個可行方案 🤔

| 方案 | 做法 | 優點 | 缺點 |
| --- | --- | --- | --- |
| **A：升級 Node** | CI 改 `node-version: '22'` | 只改一行，跟上工具鏈 | 本機/其他環境也要一併升級 |
| **B：降級 jsdom**（本次採用 ✅） | `jsdom` 30 → **26** | 維持 Node 20 不變 | 需重產 lockfile；將來升級仍可能踩雷 |

---

## 坑 3：本機執行 npm 指令時的環境限制 🪟

在實際執行方案 B 的 Windows 本機環境，還遇到兩個沙盤/權限小坑：

### 3.1 npm 全域 cache 寫入被拒（EPERM）🚫

```text
npm error code EPERM
npm error path C:\Users\...\npm-cache\_cacache\tmp\***
npm error FetchError: Invalid response body ... EPERM: operation not permitted
```

**解法**：改用工作目錄內的暫存 cache，避開被鎖的全域 cache 目錄：

```powershell
cd frontend
npm install -D jsdom@26 --cache .npm-cache
```

安裝完成後記得手動刪除 `.npm-cache`（或確認 `.gitignore` 有排除）。🧹

### 3.2 Vitest 本機啟動時 `spawn EPERM` 🔒

```text
[plugin externalize-deps]
Error: spawn EPERM
    at optimizeSafeRealPathSync (.../vite/.../node.js:2438:2)
```

Vite 載入 config 時需要 spawn 子程序去解析 realpath，在受限的執行沙盤中被擋。這是**本機環境限制，不是程式碼問題**；在 CI（ubuntu runner）或權限完整的環境執行即可。🐧

---

## 最終解法（方案 B 完整步驟）✅

### Step 1：降級 jsdom

```powershell
cd frontend
npm install -D jsdom@26 --cache .npm-cache
```

`package.json` 由：

```json
"jsdom": "^30.1.0"
```

變成：

```json
"jsdom": "^26.1.0"
```

### Step 2：確認問題依賴消失

關鍵發現 🔑：**jsdom 26 的依賴清單裡完全沒有 undici**（undici 是 jsdom 27+ 才引入的）：

```text
cssstyle, data-urls, decimal.js, html-encoding-sniffer,
http-proxy-agent, https-proxy-agent, ..., whatwg-url, ws ...
（沒有 undici）
```

- `npm ls undici` → 空的（樹上不存在 undici）✅
- jsdom 26 的 engines：`node >=18` → Node 20 完全符合 ✅

崩潰路徑（jsdom → undici → markAsUncloneable）被連根拔除。🌿

### Step 3：本機驗證測試可跑

```powershell
npx vitest run
```

```text
 ✓ src/__tests__/Login.spec.js (2 tests) 55ms

 Test Files  1 passed (1)
      Tests  2 passed (2)
```

兩個測試全數通過，沒有 unhandled errors。🎉

### Step 4：提交並推送

```powershell
# 清理暫存 cache
Remove-Item -Recurse -Force frontend\.npm-cache

git add frontend/package.json frontend/package-lock.json
git commit -m "fix(frontend): downgrade jsdom to v26 for Node 20 compatibility"
git push origin main
```

> 推送時若遇到 `schannel: SEC_E_NO_CREDENTIALS`，是執行環境擋住了 Windows 認證管理員存取，在權限完整的環境重試即可。🔑

---

## 經驗總結（一句話各坑）📝

1. 📭 **空檔案坑**：`git add` 快照的是當下內容；add 完再編輯不會自動進 commit。push 前必看 `git status` / `git diff --cached`。
2. 💣 **版本錯配坑**：看到 `xxx is not a function` 且牽扯到 Node 內建模組（`webidl`、`node:`），優先懷疑「依賴要求的 Node 版本 > 實際執行的 Node 版本」。npm 的 engines 只是警告、不是保險絲。
3. 🪟 **環境權限坑**：本機的 EPERM/spawn 錯誤先判斷是不是沙盤或檔案鎖，不要誤以為是程式碼 bug。
4. 🧭 **排查心法**：stack trace 從最底層往源頭讀；先確認「實際安裝版本」（lockfile / `npm ls`），再談程式邏輯。
5. ⚖️ **升 Node 或降依賴**沒有絕對答案：能統一升級環境就選升級；要守住舊版 runtime 就釘版本（pin）相容性依賴。

## 驗證清單 ✅

- [ ] `git status` 沒有遺漏未提交的 `ci.yml` 修改
- [ ] GitHub 上的 `ci.yml` 非空、`on:` 觸發事件完整
- [ ] `node-version` 與 lockfile 中各套件的 engines 相容
- [ ] `npm ls undici` 沒有出現需要 Node 22+ 的 undici
- [ ] `npx vitest run` 本機顯示測試通過、無 unhandled errors
- [ ] 暫存的 `.npm-cache` 已清理、未被 commit
- [ ] push 後 GitHub Actions 的 Frontend / Backend job 皆為綠燈 🟢
