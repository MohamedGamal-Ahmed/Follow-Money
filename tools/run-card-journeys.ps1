param([string]$Device = 'emulator-5556', [switch]$UiOnly)
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Synthetic card journeys require a disposable emulator.' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
$root = Split-Path $PSScriptRoot -Parent
$appId = 'com.mohamedgamal.masareef.cardreview.journeyqa'
Push-Location $root
try {
    New-Item -ItemType Directory -Force '.tmp' | Out-Null
    # Keep the owner's app and other agents' QA packages independent.
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.cardreview.journeyqa' }
    }
}
'@ | Set-Content '.tmp/card-journeys.init.gradle' -Encoding utf8
    & ./gradlew.bat --offline -I .tmp/card-journeys.init.gradle assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'QA APK build failed' }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test install failed' }
    $modes = if ($UiOnly) { @('card_ui_light','card_ui_dark','card_ui_large') } else { @('base','phase','checks','regressions','card','card_ui_light','card_ui_dark','card_ui_large','journey') }
    foreach ($mode in $modes) {
        $arguments = @()
        if ($mode -eq 'phase') { $arguments = @('-e','phase_one','true') }
        elseif ($mode -eq 'journey') { $arguments = @('-e','journey_ui','true') }
        elseif ($mode -ne 'base') { $arguments = @('-e','feature_audit',$mode) }
        $result = & $adb -s $Device shell am instrument -w @arguments "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
        $result
        $report = $result -join "`n"
        if ($LASTEXITCODE -ne 0 -or $report -notmatch '(Passed: [1-9][0-9]*|(?:Card journey UI|Journey UI) checks passed: [1-9][0-9]*); failed: 0' -or $report -match ': FAILED|INSTRUMENTATION_FAILED|Process crashed') { throw "QA failed: $mode" }
    }
} finally { Pop-Location }
