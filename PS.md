# PowerShell Script 必知必會與踩坑

本文整理在 Windows、VS Code、Maven、Docker 與 DevSecOps 專案中撰寫 PowerShell script 時最常用的語法與常見問題。

## 1. 執行 `.ps1` 腳本

在目前目錄執行腳本：

```powershell
.\mvn-local.ps1 validate
```

執行指定路徑的腳本：

```powershell
& "C:\tools\build.ps1"
```

`&` 是 call operator，當路徑含空白或命令存放在變數中時需要使用：

```powershell
$script = "C:\My Tools\build.ps1"
& $script
```

PowerShell 腳本參數會放在腳本名稱後面：

```powershell
.\mvn-local.ps1 test
.\mvn-local.ps1 org.owasp:dependency-check-maven:check
```

## 2. 目前目錄不是腳本所在目錄

### 問題

```text
 .\mvn-local.ps1 : The term '.\mvn-local.ps1' is not recognized
```

### 原因

PowerShell 的目前工作目錄不是 `mvn-local.ps1` 所在的 `backend` 目錄。`.\script.ps1` 只會從目前目錄尋找檔案。

### 解法

先切換目錄：

```powershell
Set-Location "C:\Users\.Maxfan\projects\docker-handson\devsecops-task-app\backend"
.\mvn-local.ps1 validate
```

或使用完整路徑：

```powershell
& "C:\Users\.Maxfan\projects\docker-handson\devsecops-task-app\backend\mvn-local.ps1" validate
```

腳本內若要取得自身所在目錄，不要依賴執行時的目前目錄：

```powershell
$scriptRoot = $PSScriptRoot
$configPath = Join-Path $scriptRoot "config\app.yml"
```

## 3. 變數與字串

```powershell
$name = "task-app"
$count = 3
$enabled = $true

Write-Output "Project: $name"
Write-Output "Count: $count"
```

需要明確分隔變數名稱時使用 `${}`：

```powershell
$envName = "test"
Write-Output "Deploying to ${envName}Environment"
```

單引號不會展開變數，雙引號會展開：

```powershell
$name = "backend"
'Path: $name'   # 原樣輸出 $name
"Path: $name"   # 輸出 Path: backend
```

## 4. 環境變數

目前 PowerShell process 設定環境變數：

```powershell
$env:NVD_API_KEY = "<api-key>"
```

讀取環境變數：

```powershell
$env:NVD_API_KEY
```

只檢查是否存在，不要輸出 secret：

```powershell
if ([string]::IsNullOrWhiteSpace($env:NVD_API_KEY)) {
    throw "NVD_API_KEY is not set"
}
```

### 踩坑：不同 terminal 不一定共享變數

`$env:NAME = "value"` 預設只存在於目前 PowerShell process。關閉 terminal、開啟新的 VS Code terminal，或從其他程式啟動腳本後，變數可能不存在。

需要時，設定與執行放在同一個 terminal：

```powershell
$env:NVD_API_KEY = "<new-key>"
.\mvn-local.ps1 org.owasp:dependency-check-maven:check
```

不要把 API key 寫入 `.ps1`、`pom.xml`、Git、文件或命令列 history。

## 5. 錯誤處理

建議腳本開頭設定：

```powershell
$ErrorActionPreference = "Stop"
```

這會讓非終止錯誤轉成例外，方便 CI 或上層腳本正確判斷失敗。

使用 `try/catch/finally`：

```powershell
try {
    New-Item -ItemType Directory -Path ".\output" -ErrorAction Stop
}
catch {
    Write-Error "建立 output 目錄失敗: $($_.Exception.Message)"
    exit 1
}
finally {
    Write-Output "完成清理或收尾"
}
```

### 踩坑：`$?` 與 `$LASTEXITCODE` 不同

- `$?`：上一個 PowerShell 命令是否成功
- `$LASTEXITCODE`：上一個 native executable，例如 `mvn.cmd`、`docker.exe` 的 exit code

執行 Maven、Docker 或 Semgrep 後應檢查 `$LASTEXITCODE`：

```powershell
& .\mvn.cmd test
if ($LASTEXITCODE -ne 0) {
    throw "Maven failed with exit code $LASTEXITCODE"
}
```

## 6. 執行外部命令與傳遞參數

```powershell
$maven = "C:\tools\maven\bin\mvn.cmd"
& $maven "-DskipTests" "package"
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
```

參數含有 `:`、`=` 或空白時，建議整個參數加引號：

```powershell
.\mvn-local.ps1 org.owasp:dependency-check-maven:check `
    "-DnvdApiKey=$env:NVD_API_KEY"
```

但 secret 不應直接放在命令列；優先使用 `pom.xml` 的 `${env.NVD_API_KEY}`。

### 踩坑：PowerShell 的 `;` 不是成功檢查

```powershell
command-a; command-b
```

即使 `command-a` 失敗，`command-b` 仍可能執行。需要依結果中止時：

```powershell
command-a
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
command-b
```

## 7. 路徑與檔案操作

優先使用 PowerShell cmdlet：

```powershell
New-Item -ItemType Directory -Path ".\reports" -Force
New-Item -ItemType File -Path ".\reports\scan.txt" -Force
Get-ChildItem -Path ".\reports"
Test-Path ".\reports\scan.txt"
Remove-Item ".\reports\scan.txt"
```

使用 `Join-Path` 組合路徑，避免手動拼接斜線：

```powershell
$report = Join-Path $PSScriptRoot "target\dependency-check-report.html"
```

### 踩坑：`cd` 與腳本目錄

`cd` 只改變目前 terminal 的工作目錄，不會把腳本移到該目錄。腳本需要讀取鄰近檔案時，使用 `$PSScriptRoot`。

## 8. 函式與參數

使用 `param` 宣告腳本參數：

```powershell
param(
    [Parameter(Mandatory = $true)]
    [string]$Environment,

    [switch]$SkipTests
)

Write-Output "Environment: $Environment"
```

執行：

```powershell
.\deploy.ps1 -Environment test -SkipTests
```

簡單函式：

```powershell
function Invoke-CheckedCommand {
    param(
        [string]$FilePath,
        [string[]]$ArgumentList
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "$FilePath failed with exit code $LASTEXITCODE"
    }
}
```

## 9. 管線與輸出

PowerShell 管線傳遞的是物件，不只是文字：

```powershell
Get-ChildItem | Where-Object Length -gt 1KB | Select-Object Name, Length
```

常用命令：

```powershell
Get-Process | Sort-Object CPU -Descending | Select-Object -First 5
Get-Service | Where-Object Status -eq "Running"
```

將輸出寫入檔案：

```powershell
command 2>&1 | Tee-Object -FilePath ".\build.log"
```

### 踩坑：不要用 `Write-Host` 當腳本結果

`Write-Host` 主要寫入畫面，不適合讓其他腳本接收結果。要回傳資料，使用輸出物件或 `Write-Output`；錯誤使用 `Write-Error`。

## 10. 常見執行政策問題

### 問題

```text
running scripts is disabled on this system
```

### 檢查目前政策

```powershell
Get-ExecutionPolicy -List
```

若公司政策允許，可只對目前使用者調整：

```powershell
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned
```

不要直接使用 `Unrestricted` 或任意繞過安全政策。下載外部腳本後，先檢查來源與內容。

## 11. 本專案常用腳本模式

`backend/mvn-local.ps1` 會先找到本地 JDK 和 Maven，再設定 `JAVA_HOME` 與 `PATH`，最後轉呼叫 `mvn.cmd`：

```powershell
$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = Get-ChildItem (Join-Path $backendRoot '.tools\jdk') -Directory |
    Select-Object -First 1

if ($null -eq $jdk) {
    throw 'Local JDK not found.'
}
```

這種寫法的重點是：

- 使用腳本自身位置，而不是假設目前目錄
- 啟用嚴格錯誤處理
- 先檢查工具是否存在
- 將外部命令的 exit code 傳回上層

## 12. PowerShell Script 檢查清單

- [ ] 腳本使用 `$PSScriptRoot` 處理相對檔案
- [ ] 重要腳本設定 `$ErrorActionPreference = "Stop"`
- [ ] 外部命令執行後檢查 `$LASTEXITCODE`
- [ ] secret 使用環境變數或 secret manager
- [ ] 沒有把 secret 寫入 log、命令列、Git 或文件
- [ ] 路徑使用 `Join-Path`，而不是手動拼接
- [ ] 參數使用 `param` 和型別宣告
- [ ] 破壞性命令前先確認路徑與操作範圍
- [ ] CI 執行失敗時會傳回非零 exit code
- [ ] 在乾淨 terminal 和預期的工作目錄驗證腳本
