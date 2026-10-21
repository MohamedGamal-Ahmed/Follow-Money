param([string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
if($Device -notmatch '^emulator-[0-9]+$'){throw 'Shadow store checks require a disposable emulator.'}
if(-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac.exe")){$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'}
$sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{"$env:LOCALAPPDATA/Android/Sdk"}
$adb="$sdk/platform-tools/adb.exe"
$appId='com.mohamedgamal.masareef.shadowqa'
Push-Location (Split-Path $PSScriptRoot -Parent)
try{
    if((& $adb -s $Device shell getprop sys.boot_completed) -ne '1' -or (& $adb -s $Device shell getprop ro.kernel.qemu) -ne '1'){throw 'A fully booted disposable emulator is required.'}
    New-Item -ItemType Directory -Force .tmp/shadow-store | Out-Null
    @"
allprojects { p ->
    p.plugins.withId('com.android.application') {
        p.afterEvaluate { p.android.buildTypes.debug.applicationIdSuffix = '.shadowqa' }
    }
}
"@ | ForEach-Object { [IO.File]::WriteAllText("$PWD/.tmp/shadow-store/init.gradle", $_, (New-Object Text.UTF8Encoding $false)) }
    & ./gradlew.bat --offline -I .tmp/shadow-store/init.gradle assembleDebug assembleDebugAndroidTest
    if($LASTEXITCODE -ne 0){throw 'Shadow QA build failed'}
    $metadata=Get-Content app/build/outputs/apk/debug/output-metadata.json -Raw | ConvertFrom-Json
    if($metadata.applicationId -ne $appId){throw 'Refusing to install a non-isolated APK'}
    foreach($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')){
        & $adb -s $Device install -r $apk
        if($LASTEXITCODE -ne 0){throw 'Shadow QA installation failed'}
    }
    # Invented inbox messages for the re-scan (emulator console only).
    & $adb -s $Device emu sms send QABANK 'Paid EGP 70 at QA Bakery. Card 4321.'
    & $adb -s $Device emu sms send QAPROMO 'Win a 20% cashback on your next order!'
    & $adb -s $Device emu sms send QAOTP 'Your OTP is 553019. Do not share it.'
    Start-Sleep -Seconds 3
    $result=& $adb -s $Device shell am instrument -w -e feature_audit shadow_store "$appId.test/com.mohamedgamal.masareef.ReconciliationTest"
    $result
    if($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'Shadow store checks passed: [1-9][0-9]*;' -or ($result -join "`n") -match 'FAILED|Process crashed'){throw 'Shadow store checks failed'}
}finally{Pop-Location}