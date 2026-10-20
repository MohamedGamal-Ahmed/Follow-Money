param([string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
if ($Device -notmatch '^emulator-[0-9]+$') { throw 'App-lock QA requires a disposable emulator.' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdk = $env:ANDROID_HOME
if (-not $sdk) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
$adb = "$sdk/platform-tools/adb.exe"
if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
$appId = 'com.mohamedgamal.masareef.appLockQa'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
$pinSet = $false
try {
    $boot = & $adb -s $Device shell getprop sys.boot_completed
    $qemu = & $adb -s $Device shell getprop ro.kernel.qemu
    if ($LASTEXITCODE -ne 0 -or $boot -ne '1' -or $qemu -ne '1') { throw 'A fully booted disposable emulator is required.' }
    New-Item -ItemType Directory -Force '.tmp/app-lock' | Out-Null
    @'
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application>
        <activity android:name="com.mohamedgamal.masareef.AppLockUiTest$OwnResultActivity"
            android:exported="true" android:theme="@android:style/Theme.Material.Light.NoActionBar" />
    </application>
</manifest>
'@ | Set-Content '.tmp/app-lock/qa-manifest.xml' -Encoding utf8
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.android.sourceSets.androidTest.manifest.srcFile(p.rootProject.file('.tmp/app-lock/qa-manifest.xml'))
        p.afterEvaluate {
            p.android.buildTypes.debug.applicationIdSuffix = '.appLockQa'
        }
    }
}
'@ | Set-Content '.tmp/app-lock/init.gradle' -Encoding utf8
    & ./gradlew.bat --offline -I .tmp/app-lock/init.gradle assembleDebug assembleDebugAndroidTest
    if ($LASTEXITCODE -ne 0) { throw 'App-lock QA APK build failed' }
    $metadata = Get-Content 'app/build/outputs/apk/debug/output-metadata.json' -Raw | ConvertFrom-Json
    if ($metadata.applicationId -ne $appId) { throw 'Refusing to install a non-isolated APK.' }
    & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA app installation failed' }
    & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw 'QA test installation failed' }
    $pinResult = & $adb -s $Device shell locksettings set-pin 1234
    if ($LASTEXITCODE -ne 0 -or ($pinResult -join ' ') -match 'Error|Exception') { throw 'Could not set a disposable emulator PIN' }
    $pinSet = $true
    & $adb -s $Device shell pm clear $appId | Out-Null
    $result = & $adb -s $Device shell am instrument -w -e feature_audit app_lock "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch 'App lock UI checks passed: [1-9][0-9]*' -or $report -match ': FAILED|INSTRUMENTATION_FAILED|Process crashed') { throw 'PIN and timeout UI QA failed' }
    foreach ($name in @('01-cover','02-settings')) {
        $destination = Join-Path (Get-Location) ".tmp/app-lock/$name.png"
        & cmd.exe /c "`"$adb`" -s $Device exec-out run-as $appId cat cache/app-lock/$name.png > `"$destination`""
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path $destination) -or (Get-Item $destination).Length -lt 1000) { throw "Screenshot pull failed: $name" }
    }
    & $adb -s $Device shell locksettings clear --old 1234
    if ($LASTEXITCODE -ne 0) { throw 'Could not clear disposable emulator PIN' }
    $pinSet = $false
    & $adb -s $Device shell pm clear $appId | Out-Null
    $result = & $adb -s $Device shell am instrument -w -e feature_audit app_lock_no_secure "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    $report = $result -join "`n"
    if ($LASTEXITCODE -ne 0 -or $report -notmatch 'App lock UI checks passed: [1-9][0-9]*' -or $report -match ': FAILED|INSTRUMENTATION_FAILED|Process crashed') { throw 'No-device-lock UI QA failed' }
    $destination = Join-Path (Get-Location) '.tmp/app-lock/03-no-device-lock.png'
    & cmd.exe /c "`"$adb`" -s $Device exec-out run-as $appId cat cache/app-lock/03-no-device-lock.png > `"$destination`""
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $destination) -or (Get-Item $destination).Length -lt 1000) { throw 'No-lock screenshot pull failed' }
} finally {
    if ($pinSet) { & $adb -s $Device shell locksettings clear --old 1234 | Out-Null }
    Pop-Location
}
