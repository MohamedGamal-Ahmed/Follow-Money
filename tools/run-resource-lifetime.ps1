param([string]$Device = 'emulator-5554', [switch]$Online)
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Lifetime QA requires a disposable emulator.' }
$windowsHost = $env:OS -eq 'Windows_NT'
$suffix = if ($windowsHost) { '.exe' } else { '' }
if (-not $env:JAVA_HOME -and $windowsHost) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -and $windowsHost) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb$suffix"
$appId = 'com.mohamedgamal.masareef.lifetimeqa'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    New-Item -ItemType Directory -Force .tmp | Out-Null
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.lifetimeqa' }
    }
}
'@ | ForEach-Object { [IO.File]::WriteAllText("$PWD/.tmp/lifetime.init.gradle", $_, (New-Object Text.UTF8Encoding $false)) }
    $wrapper = if ($windowsHost) { './gradlew.bat' } else { './gradlew' }
    $arguments = @('-I','.tmp/lifetime.init.gradle','assembleDebug','assembleDebugAndroidTest')
    if (-not $Online) { $arguments = @('--offline') + $arguments }
    & $wrapper @arguments
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime QA build failed' }
    $metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing non-isolated APK' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime QA install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime QA tests install failed' }
    & $adb -s $Device shell pm clear $appId
    if ($LASTEXITCODE -ne 0) { throw 'Lifetime QA reset failed' }
    $result = & $adb -s $Device shell am instrument -w -e feature_audit lifetime "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch 'Passed: 4; failed: 0' -or $report -match 'INSTRUMENTATION_FAILED|Process crashed') { throw 'Lifetime QA failed' }
} finally { Pop-Location }
