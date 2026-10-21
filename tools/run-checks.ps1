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
    $files = @('ExpenseParser','CaptureNotice','MerchantClassifier','NavigationState','TransactionJourney','DailyPoints','CsvImport','BudgetPeriod','SourceIdentity','CaptureIdentity','BalanceTimeline','CommitmentHint','MoneyTotals','SmsPreview','SmsParts','BrandCatalog','DailyAllowance','MoneyFlow','AccountMovement','RepairRules','Insights','MonthlyReport','BackupVault','Redaction','WhatsNew','ParsingPolicy','MessageNormalizer','MerchantNormalizer','MerchantCatalogIndex','ParserShadow','AmountScanner','MessageClassifier','TransactionExtractor','ConfidenceScorer','LocalLearning','ShadowDedupe','ShadowDiff','ShadowReport') | ForEach-Object { "$src/$_.java" }
    $tests = @('CaptureNoticeTest','ParserSmokeTest','TeldaCaptureTest','InstaPaySmokeTest','CsvImportSmokeTest','BudgetPeriodSmokeTest','BalanceTimelineSmokeTest','CommitmentHintSmokeTest','PhaseOneLogicTest','MoneyFlowTest','RepairRulesTest','InsightsTest','MerchantClassificationTest','MonthlyReportTest','JourneyLogicTest','DailyPointsTest','AuditStructureTest','ManualAmountTest','BackupVaultTest','RedactionTest','WhatsNewTest') | ForEach-Object { "tools/$_.java" }
    $tests += @('tools/ParserCorpusFixture.java','tools/ParserCorpusTest.java','tools/MessageShapeTest.java','tools/ParserCorpusV2Test.java','tools/MessageNormalizerTest.java','tools/MerchantNormalizerTest.java','tools/ParserShadowTest.java','tools/MessageClassifierTest.java','tools/ShadowComparison.java','tools/TransactionExtractorTest.java','tools/HeldOutBlind.java','tools/ConfidenceScorerTest.java','tools/LocalLearningTest.java','tools/ShadowDedupeTest.java','tools/AmountScannerTest.java')
    & "$env:JAVA_HOME/bin/javac$suffix" -encoding UTF-8 -d $out @files @tests
    if ($LASTEXITCODE -ne 0) { throw 'JVM compilation failed' }
    foreach ($class in @('com.mohamedgamal.masareef.CaptureNoticeTest','ParserSmokeTest','com.mohamedgamal.masareef.TeldaCaptureTest','InstaPaySmokeTest','CsvImportSmokeTest','BudgetPeriodSmokeTest','com.mohamedgamal.masareef.BalanceTimelineSmokeTest','com.mohamedgamal.masareef.CommitmentHintSmokeTest','com.mohamedgamal.masareef.PhaseOneLogicTest','com.mohamedgamal.masareef.MoneyFlowTest','com.mohamedgamal.masareef.RepairRulesTest','com.mohamedgamal.masareef.InsightsTest','com.mohamedgamal.masareef.MerchantClassificationTest','com.mohamedgamal.masareef.MonthlyReportTest','com.mohamedgamal.masareef.JourneyLogicTest','com.mohamedgamal.masareef.DailyPointsTest','AuditStructureTest','com.mohamedgamal.masareef.ManualAmountTest','com.mohamedgamal.masareef.BackupVaultTest','com.mohamedgamal.masareef.RedactionTest','com.mohamedgamal.masareef.WhatsNewTest')) {
        & "$env:JAVA_HOME/bin/java$suffix" -cp $out $class
        if ($LASTEXITCODE -ne 0) { throw "Test failed: $class" }
    }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ParserCorpusTest
    if ($LASTEXITCODE -ne 0) { throw 'Parser corpus fixture contract failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.MessageShapeTest
    if ($LASTEXITCODE -ne 0) { throw 'Message diagnostic shape checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ParserCorpusV2Test
    if ($LASTEXITCODE -ne 0) { throw 'Parser corpus v2 draft integrity failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.MessageNormalizerTest
    if ($LASTEXITCODE -ne 0) { throw 'Message normalization checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.MerchantNormalizerTest
    if ($LASTEXITCODE -ne 0) { throw 'Merchant normalization checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ParserShadowTest
    if ($LASTEXITCODE -ne 0) { throw 'Parser shadow checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.MessageClassifierTest
    if ($LASTEXITCODE -ne 0) { throw 'Message classification checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.TransactionExtractorTest
    if ($LASTEXITCODE -ne 0) { throw 'Transaction extraction checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.HeldOutBlind --require
    if ($LASTEXITCODE -ne 0) { throw 'Held-out blind set regressed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.HeldOutBlind --require --file tools/fixtures/held-out-blind-2.tsv
    if ($LASTEXITCODE -ne 0) { throw 'Held-out blind set 2 regressed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.HeldOutBlind --require --file tools/fixtures/held-out-blind-3.tsv
    if ($LASTEXITCODE -ne 0) { throw 'Held-out blind set 3 regressed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.HeldOutBlind --require --file tools/fixtures/held-out-blind-4.tsv
    if ($LASTEXITCODE -ne 0) { throw 'Held-out blind set 4 regressed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.HeldOutBlind --require --file tools/fixtures/held-out-blind-5.tsv
    if ($LASTEXITCODE -ne 0) { throw 'Held-out blind set 5 regressed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ConfidenceScorerTest
    if ($LASTEXITCODE -ne 0) { throw 'Confidence checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.LocalLearningTest
    if ($LASTEXITCODE -ne 0) { throw 'Local learning checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ShadowDedupeTest
    if ($LASTEXITCODE -ne 0) { throw 'Shadow dedupe checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.AmountScannerTest
    if ($LASTEXITCODE -ne 0) { throw 'Amount scanner checks failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ShadowComparison classification
    if ($LASTEXITCODE -ne 0) { throw 'Shadow comparison (classification) failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ShadowComparison extraction
    if ($LASTEXITCODE -ne 0) { throw 'Shadow comparison (extraction) failed' }
    & "$env:JAVA_HOME/bin/java$suffix" -cp $out com.mohamedgamal.masareef.ShadowComparison confidence
    if ($LASTEXITCODE -ne 0) { throw 'Shadow comparison (confidence) failed' }
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
        foreach ($mode in @('base','phase','features','audit','card')) {
            $arguments = @()
            if ($mode -eq 'phase') { $arguments = @('-e','phase_one','true') }
            if ($mode -eq 'features') { $arguments = @('-e','feature_audit','checks') }
            if ($mode -eq 'card') { $arguments = @('-e','feature_audit','card') }
            if ($mode -eq 'audit') { $arguments = @('-e','feature_audit','regressions') }
            $result = & $adb -s $Device shell am instrument -w @arguments com.mohamedgamal.masareef.test/com.mohamedgamal.masareef.ReconciliationTest
            $result
            $report = $result -join "`n"
            if ($LASTEXITCODE -ne 0 -or $report -notmatch 'Passed: [1-9][0-9]*; failed: 0' -or $report -match ': FAILED|INSTRUMENTATION_FAILED') { throw "Instrumentation failed: $mode" }
        }
    }
} finally { Pop-Location }
