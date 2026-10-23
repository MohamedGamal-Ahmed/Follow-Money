param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'HSBC format QA requires a disposable emulator.' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.hsbcqa'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1') { throw 'Disposable emulator must be fully booted.' }
    New-Item -ItemType Directory -Force '.tmp/hsbc-trailing-sign' | Out-Null
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.hsbcqa' }
    }
}
'@ | ForEach-Object { [IO.File]::WriteAllText("$PWD/.tmp/hsbc-format.init.gradle", $_, (New-Object Text.UTF8Encoding $false)) }
    & ./gradlew.bat --offline -I .tmp/hsbc-format.init.gradle assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'QA APK build failed' }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK.' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test install failed' }
    & $adb -s $Device shell pm grant $appId android.permission.POST_NOTIFICATIONS
    if ($LASTEXITCODE -ne 0) { throw 'QA notification permission failed' }
    $result = & $adb -s $Device shell am instrument -w -e feature_audit hsbc_ui "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch 'HSBC format UI checks passed: [1-9][0-9]*' -or $report -match ': FAILED|INSTRUMENTATION_FAILED|Process crashed') { throw 'HSBC format UI QA failed' }
    # exec-out keeps the PNG bytes intact; a PowerShell redirection would re-encode them.
    $pull = Join-Path (Get-Location) '.tmp/hsbc-trailing-sign/hsbc-rows.png'
    & cmd.exe /c "`"$adb`" -s $Device exec-out run-as $appId cat cache/hsbc-format/hsbc-rows.png > `"$pull`""
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $pull) -or (Get-Item $pull).Length -lt 1000) { throw 'Screenshot pull failed' }
} finally { Pop-Location }
