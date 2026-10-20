param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Backup QA requires a disposable emulator.' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root

function Build-Qa([string]$suffix) {
    @"
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '$suffix' }
    }
}
"@ | Set-Content ".tmp/local-backup$suffix.init.gradle" -Encoding utf8
    & ./gradlew.bat --offline -I ".tmp/local-backup$suffix.init.gradle" assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "QA APK build failed ($suffix)" }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne "com.mohamedgamal.masareef$suffix") { throw 'Refusing to install a non-isolated APK.' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test install failed' }
    & $adb -s $Device shell pm clear "com.mohamedgamal.masareef$suffix" | Out-Null
}

function Run-Mode([string]$appId, [string]$mode, [string]$expected) {
    $result = & $adb -s $Device shell am instrument -w -e feature_audit $mode "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch "$expected checks passed: [1-9][0-9]*" -or $report -match ': FAILED|INSTRUMENTATION_FAILED|Process crashed') { throw "Backup QA failed: $mode" }
}

try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1') { throw 'Disposable emulator must be fully booted.' }
    New-Item -ItemType Directory -Force '.tmp/local-backup' | Out-Null
    # 1) Same package: seed → backup → refusals leave data → wipe data + Keystore key → restore → identical ledger.
    Build-Qa '.backupqa'
    Run-Mode 'com.mohamedgamal.masareef.backupqa' 'backup_restore' 'Backup restore'
    Run-Mode 'com.mohamedgamal.masareef.backupqa' 'backup_ui' 'Backup UI'
    # exec-out keeps the PNG bytes intact; a PowerShell redirection would re-encode them.
    foreach ($name in @('01-settings-data', '02-backup-password', '03-restore-summary', '04-settings-after-backup')) {
        $pull = Join-Path (Get-Location) ".tmp/local-backup/$name.png"
        & cmd.exe /c "`"$adb`" -s $Device exec-out run-as com.mohamedgamal.masareef.backupqa cat cache/backup-ui/$name.png > `"$pull`""
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $pull) -or (Get-Item $pull).Length -lt 1000) { throw "Screenshot pull failed: $name" }
    }
    # 2) Two packages (Beta → production stand-in): export in one, import the same file in another.
    Run-Mode 'com.mohamedgamal.masareef.backupqa' 'backup_export' 'Backup export'
    $exchange = Join-Path (Get-Location) '.tmp/local-backup/exchange.fmbackup'
    & cmd.exe /c "`"$adb`" -s $Device exec-out run-as com.mohamedgamal.masareef.backupqa cat cache/exchange.fmbackup > `"$exchange`""
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $exchange) -or (Get-Item $exchange).Length -lt 1000) { throw 'Backup file pull failed' }
    Build-Qa '.backupqa2'
    & $adb -s $Device push $exchange /data/local/tmp/exchange.fmbackup | Out-Null
    # A freshly cleared package has no cache folder yet; create it as the app user before copying the file in.
    & $adb -s $Device shell run-as com.mohamedgamal.masareef.backupqa2 mkdir -p cache
    & $adb -s $Device shell run-as com.mohamedgamal.masareef.backupqa2 cp /data/local/tmp/exchange.fmbackup cache/exchange.fmbackup
    if ($LASTEXITCODE -ne 0) { throw 'Backup file hand-over failed' }
    & $adb -s $Device shell rm /data/local/tmp/exchange.fmbackup | Out-Null
    Run-Mode 'com.mohamedgamal.masareef.backupqa2' 'backup_import' 'Backup import'
    Remove-Item $exchange -Force
    foreach ($suffix in @('.backupqa', '.backupqa2')) {
        & $adb -s $Device uninstall "com.mohamedgamal.masareef$suffix.test" | Out-Null
        & $adb -s $Device uninstall "com.mohamedgamal.masareef$suffix" | Out-Null
    }
} finally { Pop-Location }
