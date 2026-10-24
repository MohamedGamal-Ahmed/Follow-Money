param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw "What's new QA requires a disposable emulator." }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.whatsNewQa'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    $qemu = & $adb -s $Device shell getprop ro.kernel.qemu
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1' -or $qemu -ne '1') { throw 'A fully booted disposable emulator is required.' }
    New-Item -ItemType Directory -Force '.tmp/whats-new' | Out-Null
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.whatsNewQa' }
    }
}
'@ | ForEach-Object { [IO.File]::WriteAllText("$PWD/.tmp/whats-new/init.gradle", $_, (New-Object Text.UTF8Encoding $false)) }
    & ./gradlew.bat --offline -I .tmp/whats-new/init.gradle assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw "What's new QA build failed" }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK.' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test install failed' }
    & $adb -s $Device shell pm clear $appId
    $result = & $adb -s $Device shell am instrument -w -e feature_audit whats_new "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch "What's new UI checks passed: [1-9][0-9]*" -or ($result -join "`n") -match 'FAILED|Process crashed') { throw "What's new QA failed" }
    $destination = Join-Path (Get-Location) '.tmp/whats-new/05-whats-new-card.png'
    & cmd.exe /c "`"$adb`" -s $Device exec-out run-as $appId cat cache/whats-new/05-whats-new-card.png > `"$destination`""
    if ($LASTEXITCODE -ne 0 -or (Get-Item $destination).Length -lt 1000) { throw 'Screenshot pull failed' }
} finally { Pop-Location }
