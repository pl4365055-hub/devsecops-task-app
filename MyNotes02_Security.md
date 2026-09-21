# 🛡️ 應用安全三掃描：SAST、依賴掃描、映像掃描

你貼的是三個概念，我把它們當成一套 CI/CD 安全掃描來解釋，對比部分也在三者之間進行。

## 🔤 名詞中英對照

- 靜態程式碼安全掃描 (SAST, Static Application Security Testing)
- 依賴漏洞掃描 (Dependency Scan, 又稱 SCA, Software Composition Analysis)
- 容器映像漏洞掃描 (Container Scan)

## 🧠 第一性原理

軟體 = 你寫的碼 + 借來的碼 + 承載它的環境。三處都可能有洞，所以要各掃一次。

## 📖 What：它們是什麼

- **SAST** 🔍：不執行程式，直接讀你的原始碼。它找出危險寫法，例如 SQL 字串拼接、寫死的密碼、不安全的加密函式。
- **Dependency Scan** 📦：讀 `package-lock.json`、`pom.xml`、`requirements.txt` 這類清單，比對已知漏洞資料庫（CVE）。它抓的是你「借來的套件」裡的洞。
- **Container Scan** 🐳：拆開打包好的 Docker 映像，逐層檢查。它會看到作業系統套件（如 openssl）、執行環境和裡面的應用程式庫。

## 💥 So what：為什麼重要

- 現代專案有八成以上的程式碼來自第三方。你沒寫，但你負責。
- 漏洞越晚發現，修復成本越高。上線後才發現，可能要緊急停機。
- 三者互補。你的程式碼很乾淨，但基底映像可能有舊版 OpenSSL。
- 資安稽核和客戶審查，越來越常要求提供掃描報告。

## ✅ Now what：現在該怎麼做

1. **先接進 CI，只擋高風險。** 🚦 pipeline 只在 Critical / High 時失敗。一開始全擋，開發者只會想辦法繞過。
2. **讓修補自動化。** 🤖 開啟 Dependabot 或 Renovate，自動發升級 PR。同時把基底映像換成精簡版（alpine、distroless），攻擊面直接縮小。
3. **排程重掃已上線的東西。** ⏰ 新 CVE 每天都在出。每週重掃一次線上映像和依賴，別只在 build 時掃一次。

## ⚖️ 概念對比

我多放了一欄 DAST，它最容易和 SAST 搞混。

| | SAST | Dependency Scan | Container Scan | DAST（對照組） |
|---|---|---|---|---|
| 🎯 掃什麼 | 你寫的原始碼 | 第三方套件清單 | 完整映像（OS + 套件） | 執行中的應用 |
| ⏱️ 何時掃 | 寫完碼、commit 時 | commit / build 時 | 映像 build 後、部署前 | 部署到測試環境後 |
| 🐛 找什麼 | 不安全的寫法 | 已知 CVE | OS 層與套件層 CVE | 執行期行為漏洞 |
| 🧰 常見工具 | Semgrep、SonarQube、CodeQL | Dependabot、Snyk、OWASP Dependency-Check | Trivy、Grype | OWASP ZAP |
| 🎭 誤報程度 | 偏高 | 中等 | 中等 | 偏低 |

一句話區分：SAST 看「程式怎麼寫」，DAST 看「程式怎麼跑」，其餘兩個看「用了什麼零件」。

## 🌍 實際用例

2021 年 Log4Shell（CVE-2021-44228）爆發時，全球團隊都在問「我們有用 log4j 嗎？」

- 有接 **Dependency Scan** 的團隊，幾分鐘內就從報告裡看到哪些服務用了受影響的版本。
- 有接 **Container Scan** 的團隊，還能抓到藏在第三方映像裡、自己根本不知道的 log4j。
- 沒掃描的團隊，只能人工翻程式碼，花了好幾天。

GitLab、GitHub 都把這三種掃描做成內建範本，幾行 YAML 就能啟用。

## ⚠️ 誤區與踩坑點

- 🚨 **警報疲勞**：報告一次列出幾百筆，大家就不看了。先處理 Critical / High，其餘排進 backlog。
- 🎯 **有 CVE ≠ 一定被攻擊**：漏洞的函式你可能根本沒呼叫。要看「可達性」（reachability）再排優先順序。
- 🔒 **沒有 lockfile，掃描就不準**：Dependency Scan 靠鎖定版本才能比對，沒 lockfile 就等於盲掃。
- 🕰️ **只在 build 時掃一次**：昨天乾淨的映像，今天可能因為新 CVE 公布而變髒。要定期重掃。
- 🙈 **無限期忽略誤報**：allowlist 每一筆都要附原因和到期日，否則會變成永久的洞。
- 🧩 **以為一個工具就夠**：三種掃描的盲區不同，缺一個就少一層防線。