param([string]$Device='emulator-5556')
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-[0-9]+$'){throw 'Notification capture checks require a disposable emulator.'}
$windowsHost=$env:OS -eq 'Windows_NT'
$suffix=if($windowsHost){'.exe'}else{''}
if($windowsHost -and -not $env:JAVA_HOME){$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'}
$sdk=$env:ANDROID_HOME
if(-not $sdk){$sdk=$env:ANDROID_SDK_ROOT}
if(-not $sdk -and $windowsHost){$sdk="$env:LOCALAPPDATA/Android/Sdk"}
$adb="$sdk/platform-tools/adb$suffix"
$appId='com.mohamedgamal.masareef.notificationqa'
Push-Location (Split-Path $PSScriptRoot -Parent)
try{
    if((& $adb -s $Device shell getprop sys.boot_completed) -ne '1'){throw 'Disposable emulator must be booted.'}
    New-Item -ItemType Directory -Force .tmp/notification-capture | Out-Null
    @'
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.notificationqa' }
    }
}
'@ | Set-Content .tmp/notification-capture/init.gradle -Encoding utf8
    $wrapper=if($windowsHost){'./gradlew.bat'}else{'./gradlew'}
    & $wrapper --offline -I .tmp/notification-capture/init.gradle assembleDebug assembleDebugAndroidTest
    if($LASTEXITCODE -ne 0){throw 'Notification QA build failed'}
    $metadata=Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if($metadata.applicationId -ne $appId){throw 'Refusing to install a non-isolated APK'}
    foreach($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')){
        & $adb -s $Device install -r $apk
        if($LASTEXITCODE -ne 0){throw 'Notification QA installation failed'}
    }
    $result=& $adb -s $Device shell am instrument -w -e feature_audit notification_capture "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Notification capture checks passed: [1-9][0-9]*;' -or ($result -join "`n") -match 'FAILED|Process crashed'){throw 'Notification listener capture checks failed'}
    $badges=& $adb -s $Device shell am instrument -w -e feature_audit merchant_badges "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $badges
    if($LASTEXITCODE -ne 0 -or ($badges -join "`n") -notmatch 'Merchant badge checks passed: [1-9][0-9]*;' -or ($badges -join "`n") -match 'FAILED|Process crashed'){throw 'Merchant badge checks failed'}
}finally{Pop-Location}
