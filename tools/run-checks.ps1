param([switch]$Android, [switch]$Online, [string]$Device = 'emulator-5554')
$ErrorActionPreference = 'Stop'
$windowsHost = $env:OS -eq 'Windows_NT'
$suffix = if ($windowsHost) { '.exe' } else { '' }
if ($Android -and $Device -notmatch '^emulator-[0-9]+$') { throw 'Write-oriented Android tests require a disposable emulator, not a physical device.' }
if (-not $env:JAVA_HOME -and $windowsHost) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME/bin/javac$suffix")) { throw 'Set JAVA_HOME to a Java 17 installation.' }
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $out = '.tmp/qa-jvm'
    New-Item -ItemType Directory -Force $out | Out-Null
    $src = 'app/src/main/java/com/mohamedgamal/masareef'
    $files = @('ExpenseParser','MerchantClassifier','NavigationState','TransactionJourney','DailyPoints','CsvImport','BudgetPeriod','SourceIdentity','CaptureIdentity','BalanceTimeline','CommitmentHint','MoneyTotals','SmsPreview','SmsParts','BrandCatalog','DailyAllowance','MoneyFlow','AccountMovement','RepairRules','Insights','MonthlyReport') | ForEach-Object { "$src/$_.java" }
    $tests = @('ParserSmokeTest','InstaPaySmokeTest','CsvImportSmokeTest','BudgetPeriodSmokeTest','BalanceTimelineSmokeTest','CommitmentHintSmokeTest','PhaseOneLogicTest','MoneyFlowTest','RepairRulesTest','InsightsTest','MerchantClassificationTest','MonthlyReportTest','JourneyLogicTest','DailyPointsTest','AuditStructureTest') | ForEach-Object { "tools/$_.java" }
    & "$env:JAVA_HOME/bin/javac$suffix" -encoding UTF-8 -d $out @files @tests
    if ($LASTEXITCODE -ne 0) { throw 'JVM compilation failed' }
    foreach ($class in @('ParserSmokeTest','InstaPaySmokeTest','CsvImportSmokeTest','BudgetPeriodSmokeTest','com.mohamedgamal.masareef.BalanceTimelineSmokeTest','com.mohamedgamal.masareef.CommitmentHintSmokeTest','com.mohamedgamal.masareef.PhaseOneLogicTest','com.mohamedgamal.masareef.MoneyFlowTest','com.mohamedgamal.masareef.RepairRulesTest','com.mohamedgamal.masareef.InsightsTest','com.mohamedgamal.masareef.MerchantClassificationTest','com.mohamedgamal.masareef.MonthlyReportTest','com.mohamedgamal.masareef.JourneyLogicTest','com.mohamedgamal.masareef.DailyPointsTest','AuditStructureTest')) {
        & "$env:JAVA_HOME/bin/java$suffix" -cp $out $class
        if ($LASTEXITCODE -ne 0) { throw "Test failed: $class" }
    }
    $wrapper = if ($windowsHost) { './gradlew.bat' } else { './gradlew' }
    $gradleArguments = @('assembleDebug','lintDebug','assembleDebugAndroidTest')
    if (-not $Online) { $gradleArguments = @('--offline') + $gradleArguments }
    & $wrapper @gradleArguments
    if ($LASTEXITCODE -ne 0) { throw 'Android build/lint failed' }
    if ($Android) {
        $sdk = $env:ANDROID_HOME
        if (-not $sdk) { $sdk = $env:ANDROID_SDK_ROOT }
        if (-not $sdk -and $windowsHost) { $sdk = "$env:LOCALAPPDATA/Android/Sdk" }
        $adb = "$sdk/platform-tools/adb$suffix"
        if (-not (Test-Path $adb)) { throw 'Set ANDROID_HOME to an SDK with platform-tools.' }
        & $adb -s $Device install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'App installation failed' }
        & $adb -s $Device install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw 'Test installation failed' }
        foreach ($mode in @('base','phase','features','audit')) {
            $arguments = @()
            if ($mode -eq 'phase') { $arguments = @('-e','phase_one','true') }
            if ($mode -eq 'features') { $arguments = @('-e','feature_audit','checks') }
            if ($mode -eq 'audit') { $arguments = @('-e','feature_audit','regressions') }
            $result = & $adb -s $Device shell am instrument -w @arguments com.mohamedgamal.masareef.test/com.mohamedgamal.masareef.ReconciliationTest
            $result
            $report = $result -join "`n"
            if ($LASTEXITCODE -ne 0 -or $report -notmatch 'Passed: [1-9][0-9]*; failed: 0' -or $report -match ': FAILED|INSTRUMENTATION_FAILED') { throw "Instrumentation failed: $mode" }
        }
    }
} finally { Pop-Location }
