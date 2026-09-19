# Lab 08 - OWASP Dependency-Check 與 NVD API 踩坑

本文記錄使用 Maven OWASP Dependency-Check 掃描 Spring Boot backend 時遇到的問題、原因、解法與驗證方式。

## 1. Dependency-Check plugin 放在錯誤的 XML 位置

### 問題

Maven 一開始無法讀取 `pom.xml`：

```text
Malformed POM
Unrecognised tag: 'plugin'
```

### 原因

Dependency-Check 的 `<plugin>` 被放在 `</plugins>` 之後。Maven plugin 必須放在：

```xml
<build>
    <plugins>
        <plugin>
            ...
        </plugin>
    </plugins>
</build>
```

### 解法

將 Dependency-Check plugin 放入既有的 `<plugins>` 容器：

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>12.1.0</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <nvdMaxRetryCount>10</nvdMaxRetryCount>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

先執行 POM 驗證：

```powershell
.\mvn-local.ps1 validate
```

### 版本注意

Dependency-Check plugin `9.0.9` 太舊，不建議繼續使用。舊版可能與目前的 NVD API 更新流程相容性較差，也缺少較新的錯誤處理與重試設定。

本專案已升級至 `12.1.0`：

```xml
<version>12.1.0</version>
```

升級 plugin 版本只能改善 Dependency-Check 與 NVD API 的相容性；如果 API key 無效、請求被限流，或網路被 proxy/防火牆阻擋，仍然需要另外處理。

## 2. NVD API 回傳 403 或 404

### 問題

POM 修正後，Dependency-Check 開始更新 NVD 資料，但出現：

```text
Error updating the NVD Data; the NVD returned a 403 or 404 error
NoDataException: No documents exist
```

### 原因

常見原因包括：

- 沒有提供 NVD API key
- API key 無效、已撤銷或已過期
- API key 剛建立，尚未啟用
- 請求頻率過高，觸發 NVD rate limit
- 公司 proxy、VPN、防火牆或目前 IP 阻擋 NVD API
- Dependency-Check 尚未建立本機 NVD 資料庫

這不是 CVSS 漏洞門檻造成的失敗；掃描在下載 NVD 資料階段就已中止。

## 3. API key 應使用環境變數

### 解法

在 `pom.xml` 使用環境變數，不要把 key 寫死：

```xml
<configuration>
    <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
    <nvdApiDelay>10000</nvdApiDelay>
    <failBuildOnCVSS>7</failBuildOnCVSS>
</configuration>
```

在同一個 PowerShell 視窗設定 key 並執行：

```powershell
$env:NVD_API_KEY = "<new-nvd-api-key>"
.\mvn-local.ps1 org.owasp:dependency-check-maven:check
```

也可以直接使用 Maven property，但不建議，因為 key 會進入 shell history：

```powershell
.\mvn-local.ps1 org.owasp:dependency-check-maven:check `
    "-DnvdApiKey=$env:NVD_API_KEY"
```

## 4. PowerShell 環境變數作用域

### 問題

在一個終端設定 `$env:NVD_API_KEY`，再由另一個終端執行 Maven，Maven 可能讀不到 key。

### 原因

```powershell
$env:NVD_API_KEY = "..."
```

預設只存在於目前 PowerShell process。關閉終端、開啟新終端或使用另一個 VS Code terminal 後，變數不一定存在。

### 檢查

只檢查是否存在，不要輸出 key 內容：

```powershell
if ([string]::IsNullOrWhiteSpace($env:NVD_API_KEY)) {
    'NVD_API_KEY is not set'
} else {
    'NVD_API_KEY is set'
}
```

設定與執行必須在同一個 PowerShell 視窗完成。

## 5. 增加 NVD API delay

### 問題

即使 API key 有效，短時間大量請求仍可能觸發 403。

### 解法

設定較大的請求間隔：

```xml
<nvdApiDelay>10000</nvdApiDelay>
```

10 秒會使首次資料更新較慢，但可以降低 rate limit 風險。第一次更新完成後，Dependency-Check 可以使用本機資料庫，後續執行通常不需要重新下載全部資料。

## 6. Retry 次數過高會讓執行很久

### 問題

NVD API 連線失敗或回傳 403/404 時，Dependency-Check 會依設定重試。若 retry 次數過高，每次又有 API delay，整個 Maven 執行可能等待很久。

### 解法

使用 Dependency-Check `12.1.0`，並明確設定最大重試次數：

```xml
<version>12.1.0</version>
<configuration>
    <nvdApiKey>${env.NVD_API_KEY}</nvdApiKey>
    <nvdApiDelay>10000</nvdApiDelay>
    <nvdMaxRetryCount>10</nvdMaxRetryCount>
</configuration>
```

`nvdMaxRetryCount` 會限制 NVD 更新失敗時的重試次數。數值越大，暫時性網路錯誤的容錯性越高，但 key 無效或 NVD 被阻擋時也會等待更久。若只是本機快速診斷，可以暫時使用較小的值，例如 `1` 或 `2`；CI 環境則應依網路穩定性設定。

## 7. API key 已經暴露時的處理

### 問題

API key 出現在聊天內容、PowerShell history、命令列參數或 log 中，就不應再繼續使用。

### 解法

1. 到 NVD 帳戶撤銷已暴露的 key。
2. 重新產生新的 API key。
3. 使用環境變數，不要提交到 Git。
4. 清理 PowerShell history 或其他包含 key 的 log。
5. 不要將 key 寫入 `pom.xml`、README、`.md` 文件或 CI log。

## 8. 建議診斷順序

遇到 Dependency-Check 失敗時，依序確認：

1. `pom.xml` 是否能通過 `mvn validate`
2. Dependency-Check plugin 版本與 Maven 是否可下載
3. `NVD_API_KEY` 是否存在於目前 PowerShell process
4. API key 是否仍有效且未暴露
5. `nvdApiDelay` 是否足夠大
6. 是否可以連線到 `https://services.nvd.nist.gov`
7. 公司 proxy、VPN 或防火牆是否阻擋 NVD
8. NVD 資料更新成功後，再處理真正的 CVSS 漏洞報告

## 9. 驗證清單

- [ ] Dependency-Check plugin 位於 `<build><plugins>` 內
- [ ] `mvn validate` 成功
- [ ] Dependency-Check plugin 使用 `12.1.0`
- [ ] 沒有把 API key 寫入 repository
- [ ] API key 由 `NVD_API_KEY` 提供
- [ ] 設定與 Maven 執行在同一個 PowerShell 視窗
- [ ] `nvdApiDelay` 已設定為合理值
- [ ] `nvdMaxRetryCount` 已設定，避免失敗時無限等待
- [ ] 暴露過的 API key 已撤銷
- [ ] NVD 資料更新成功
- [ ] 更新成功後才根據 CVSS >= 7 判斷是否修正依賴
