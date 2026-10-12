param([switch]$Unsigned, [switch]$Online)
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\java.exe")) { throw 'Set JAVA_HOME to your Java installation.' }
$names = @('FOLLOW_MONEY_UPLOAD_KEYSTORE','FOLLOW_MONEY_UPLOAD_STORE_PASSWORD','FOLLOW_MONEY_UPLOAD_KEY_ALIAS','FOLLOW_MONEY_UPLOAD_KEY_PASSWORD')
$present = @($names | Where-Object { -not [string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($_)) })
if ($Unsigned -and $present.Count -gt 0) { throw 'Unset all upload signing variables for unsigned validation.' }
if (-not $Unsigned -and $present.Count -ne $names.Count) { throw 'Set all four FOLLOW_MONEY_UPLOAD_* variables locally. Never paste passwords or keys into chat or commit them.' }
if (-not $Unsigned -and -not (Test-Path -LiteralPath $env:FOLLOW_MONEY_UPLOAD_KEYSTORE -PathType Leaf)) { throw 'The upload keystore path does not exist.' }
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $arguments = @('bundleRelease','assembleRelease','lintRelease')
    if (-not $Online) { $arguments = @('--offline') + $arguments }
    & .\gradlew.bat @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Release build or lint failed.' }
    if (-not $Unsigned) {
        $verification = & "$env:JAVA_HOME\bin\jarsigner.exe" -verify app\build\outputs\bundle\release\app-release.aab
        $verification
        if ($LASTEXITCODE -ne 0 -or ($verification -join "`n") -notmatch 'jar verified' -or ($verification -join "`n") -match 'jar is unsigned') { throw 'AAB signature verification failed.' }
        Write-Output 'Signed AAB built. Verify the signer fingerprint against your Play upload certificate before upload.'
    } else {
        Write-Output 'UNSIGNED validation artifacts only. Not uploadable to Google Play.'
    }
} finally { Pop-Location }
