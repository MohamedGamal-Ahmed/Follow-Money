param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'Support QA requires a disposable emulator.' }
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.supportQa'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
$restoreBattery = $false
$wasExempt = $false
$headsUp = $null
try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    $qemu = & $adb -s $Device shell getprop ro.kernel.qemu
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1' -or $qemu -ne '1') { throw 'A fully booted disposable emulator is required.' }
    New-Item -ItemType Directory -Force '.tmp/support-aids' | Out-Null
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.supportQa' }
    }
}
'@ | Set-Content '.tmp/support-aids/init.gradle' -Encoding utf8
    & ./gradlew.bat --offline -I .tmp/support-aids/init.gradle assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'Support QA build failed' }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK.' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app install failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test install failed' }
    & $adb -s $Device shell pm clear $appId
    & $adb -s $Device shell pm grant $appId android.permission.READ_SMS
    if ($LASTEXITCODE -ne 0) { throw 'QA SMS read permission unavailable' }
    & $adb -s $Device shell pm grant $appId android.permission.POST_NOTIFICATIONS
    if ($LASTEXITCODE -ne 0) { throw 'QA notification permission unavailable' }
    $whitelist = (& $adb -s $Device shell dumpsys deviceidle whitelist) -join "`n"
    $wasExempt = $whitelist -match (',' + [regex]::Escape($appId) + ',')
    $restoreBattery = $true
    $headsUp = & $adb -s $Device shell settings get global heads_up_notifications_enabled
    & $adb -s $Device shell settings put global heads_up_notifications_enabled 0
    & $adb -s $Device shell dumpsys deviceidle whitelist "-$appId" | Out-Null
    $crash = & $adb -s $Device shell am instrument -w -e feature_audit support_crash "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $crash
    if (($crash -join "`n") -notmatch 'Process crashed') { throw 'Expected debug crash was not observed' }
    foreach ($fixture in @(
        @('HSBC','Payment format SUPPORT_QA ref 12345678 from SECRET PERSON amount EGP 120.00'),
        @('HSBC','OTP verification code 987654 for payment'),
        @('HSBC','Offer cashback up to EGP 5000 on your card purchase'),
        @('+201234567890','Payment format PERSONAL_QA ref 12345678 from SECRET PERSON')
    )) {
        & $adb -s $Device emu sms send $fixture[0] $fixture[1]
        if ($LASTEXITCODE -ne 0) { throw 'Synthetic inbox fixture failed' }
    }
    foreach ($mode in @('support_ui','support_battery_allowed')) {
        if ($mode -eq 'support_battery_allowed') { & $adb -s $Device shell dumpsys deviceidle whitelist "+$appId" | Out-Null }
        $result = & $adb -s $Device shell am instrument -w -e feature_audit $mode "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
        $result
        if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Support aids UI checks passed: [1-9][0-9]*' -or ($result -join "`n") -match 'FAILED|Process crashed') { throw "Support QA failed: $mode" }
    }
    foreach ($name in @('01-crash-preview','02-battery-restricted','03-battery-exempt','04-redacted-message')) {
        $destination = Join-Path (Get-Location) ".tmp/support-aids/$name.png"
        & cmd.exe /c "`"$adb`" -s $Device exec-out run-as $appId cat cache/support-aids/$name.png > `"$destination`""
        if ($LASTEXITCODE -ne 0 -or (Get-Item $destination).Length -lt 1000) { throw "Screenshot pull failed: $name" }
    }
} finally {
    if ($restoreBattery) {
        $prefix = if ($wasExempt) { '+' } else { '-' }
        & $adb -s $Device shell dumpsys deviceidle whitelist "$prefix$appId" | Out-Null
        if ($headsUp -eq 'null') { & $adb -s $Device shell settings delete global heads_up_notifications_enabled | Out-Null }
        elseif ($null -ne $headsUp) { & $adb -s $Device shell settings put global heads_up_notifications_enabled $headsUp | Out-Null }
    }
    Pop-Location
}
