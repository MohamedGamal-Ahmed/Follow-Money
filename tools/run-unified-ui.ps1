param([string]$Device = 'emulator-5556', [switch]$SkipBuild,
    [string[]]$Modes = @('source_link_light','source_link_dark','source_link_max','transactions_light','transactions_dark','transactions_max','unified_light','unified_dark','unified_max','menus_max'))
$ErrorActionPreference = 'Stop'
$windowsHost = $env:OS -eq 'Windows_NT'
$suffix = if ($windowsHost) { '.exe' } else { '' }
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Unified UI tests require a disposable emulator.' }
if ($windowsHost -and (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac$suffix"))) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -and $windowsHost) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb$suffix"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an Android SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.cardreview.journeyqa'
Push-Location (Split-Path $PSScriptRoot -Parent)
$originalScale = $null
try {
    if (-not $SkipBuild) {
        New-Item -ItemType Directory -Force '.tmp' | Out-Null
        @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.cardreview.journeyqa' }
    }
}
'@ | Set-Content '.tmp/unified-ui.init.gradle' -Encoding utf8
        $wrapper = if ($windowsHost) { './gradlew.bat' } else { './gradlew' }
        & $wrapper --offline -I .tmp/unified-ui.init.gradle assembleDebug assembleDebugAndroidTest
        if ($LASTEXITCODE -ne 0) { throw 'Unified UI APK build failed' }
    }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK' }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $adb -s $Device install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "QA install failed: $apk" }
    }
    $originalScale = (& $adb -s $Device shell settings get system font_scale).Trim()
    if ($LASTEXITCODE -ne 0 -or $originalScale -notmatch '^(null|[0-9]+(?:\.[0-9]+)?)$') { throw 'Could not capture emulator font scale' }
    foreach ($mode in $Modes) {
        $scale = if ($mode.EndsWith('_max')) { '2.0' } else { '1.0' }
        & $adb -s $Device shell settings put system font_scale $scale
        if ($LASTEXITCODE -ne 0) { throw 'Could not set font scale' }
        & $adb -s $Device shell am force-stop $appId
        $result = & $adb -s $Device shell am instrument -w -e feature_audit $mode "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
        $result
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch '(Unified UI|Selection UI|Transaction pages|Source linking) checks passed: [1-9][0-9]*; failed: 0') { throw "Unified UI failed: $mode" }
    }
} finally {
    if ($null -ne $originalScale) {
        if ($originalScale -eq 'null') { & $adb -s $Device shell settings delete system font_scale }
        else { & $adb -s $Device shell settings put system font_scale $originalScale }
    }
    Pop-Location
}
