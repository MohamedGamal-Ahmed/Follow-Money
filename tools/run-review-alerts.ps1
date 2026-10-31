param([string]$Device = 'emulator-5554', [switch]$Online, [switch]$SkipBuild,
    [ValidateSet('review_alerts_light','review_alerts_dark','review_alerts_large')]
    [string[]]$Modes = @('review_alerts_light','review_alerts_dark','review_alerts_large'))
$ErrorActionPreference = 'Stop'
$windowsHost = $env:OS -eq 'Windows_NT'
$suffix = if ($windowsHost) { '.exe' } else { '' }
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Review/calendar tests require a disposable emulator.' }
if ($windowsHost -and (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac$suffix"))) {
    $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
}
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
if (-not $sdk -and $windowsHost) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb$suffix"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.reviewalertsqa'
$originalScale = $null
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1') { throw 'Disposable emulator must be fully booted.' }
    New-Item -ItemType Directory -Force .tmp/review-alerts | Out-Null
    if (-not $SkipBuild) {
        @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.reviewalertsqa' }
    }
}
'@ | ForEach-Object { [IO.File]::WriteAllText("$PWD/.tmp/review-alerts/init.gradle", $_, (New-Object Text.UTF8Encoding $false)) }
        $wrapper = if ($windowsHost) { './gradlew.bat' } else { './gradlew' }
        $buildArgs = @('-I','.tmp/review-alerts/init.gradle','assembleDebug','assembleDebugAndroidTest')
        if (-not $Online) { $buildArgs = @('--offline') + $buildArgs }
        & $wrapper @buildArgs
        if ($LASTEXITCODE -ne 0) { throw 'Review/calendar QA build failed.' }
    }
    $metadata = Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK.' }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $adb -s $Device install -r $apk
        if ($LASTEXITCODE -ne 0) { throw "QA install failed: $apk" }
    }
    $originalScale = (& $adb -s $Device shell settings get system font_scale).Trim()
    if ($LASTEXITCODE -ne 0 -or $originalScale -notmatch '^(null|[0-9]+(?:\.[0-9]+)?)$') { throw 'Could not read emulator font scale.' }
    foreach ($mode in $Modes) {
        # A crashed run must not contaminate the next mode. Only this disposable QA package is cleared.
        $cleared = & $adb -s $Device shell pm clear $appId
        if ($LASTEXITCODE -ne 0 -or $cleared -notcontains 'Success') { throw 'Could not reset isolated QA data.' }
        & $adb -s $Device shell pm grant $appId android.permission.POST_NOTIFICATIONS
        if ($LASTEXITCODE -ne 0) { throw 'Could not grant QA notification permission.' }
        $scale = if ($mode -eq 'review_alerts_large') { '1.3' } else { '1.0' }
        & $adb -s $Device shell settings put system font_scale $scale
        if ($LASTEXITCODE -ne 0) { throw 'Could not set emulator font scale.' }
        $result = & $adb -s $Device shell am instrument -w -e feature_audit $mode "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
        $instrumentExit = $LASTEXITCODE
        $result
        $report = $result -join "`n"
        [IO.File]::WriteAllText("$PWD/.tmp/review-alerts/$mode.log", $report, (New-Object Text.UTF8Encoding $false))
        if ($instrumentExit -ne 0 -or $report -notmatch 'Review/alerts/calendar UI checks passed: [1-9][0-9]*; failed: 0' -or
            $report -match 'UI FAILED|INSTRUMENTATION_FAILED|INSTRUMENTATION_CODE:\s*0|Process crashed') {
            throw "Review/calendar UI failed: $mode"
        }
    }
} finally {
    if ($null -ne $originalScale) {
        if ($originalScale -eq 'null') { & $adb -s $Device shell settings delete system font_scale }
        else { & $adb -s $Device shell settings put system font_scale $originalScale }
    }
    Pop-Location
}
