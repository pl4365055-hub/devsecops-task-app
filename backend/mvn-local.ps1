$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$jdk = Get-ChildItem (Join-Path $backendRoot '.tools\jdk') -Directory | Select-Object -First 1
$maven = Get-ChildItem (Join-Path $backendRoot '.tools\maven') -Directory | Select-Object -First 1

if ($null -eq $jdk -or $null -eq $maven) {
    throw 'Local JDK/Maven not found under backend/.tools.'
}

$env:JAVA_HOME = $jdk.FullName
$env:Path = "$($jdk.FullName)\bin;$($maven.FullName)\bin;$env:Path"

& (Join-Path $maven.FullName 'bin\mvn.cmd') @args
exit $LASTEXITCODE
