param([string]$SigningDirectory = "$env:LOCALAPPDATA\FollowMoney\beta-signing")
$ErrorActionPreference = 'Stop'
$javaMajor = 0
if ($env:JAVA_HOME -and (Test-Path -LiteralPath "$env:JAVA_HOME\release")) {
    $javaRelease = Get-Content -LiteralPath "$env:JAVA_HOME\release" -Raw
    if ($javaRelease -match 'JAVA_VERSION="([0-9]+)') { $javaMajor = [int]$Matches[1] }
}
if ($javaMajor -lt 17) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$apksigner = Join-Path $sdkRoot 'build-tools\36.1.0\apksigner.bat'
if (-not (Test-Path -LiteralPath $apksigner)) { throw 'Build tools 36.1.0 must already be installed. No downloads are performed.' }
$keystore = Join-Path $SigningDirectory 'follow-money-beta.p12'
$passwordFile = Join-Path $SigningDirectory 'password.dpapi.xml'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $passwordFile)) { throw 'Run tools/create-beta-key.ps1 once before building. Preserve the same key for subsequent Beta updates.' }
$names = @('FOLLOW_MONEY_BETA_KEYSTORE','FOLLOW_MONEY_BETA_STORE_PASSWORD','FOLLOW_MONEY_BETA_KEY_ALIAS','FOLLOW_MONEY_BETA_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $sourceCommit = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Cannot identify source commit.' }
    $sourceBranch = (& git branch --show-current).Trim()
    & git diff --quiet HEAD
    $sourceDirty = $LASTEXITCODE -ne 0
    $untrackedSource = @(& git ls-files --others --exclude-standard -- app tools)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot inspect untracked source.' }
    $sourceDirty = $sourceDirty -or $untrackedSource.Count -gt 0
    $securePassword = Import-Clixml -LiteralPath $passwordFile
    if ($securePassword -isnot [Security.SecureString]) { throw 'Expected a Windows DPAPI SecureString password file.' }
    $password = [Net.NetworkCredential]::new('',$securePassword).Password
    $env:FOLLOW_MONEY_BETA_KEYSTORE = [IO.Path]::GetFullPath($keystore)
    $env:FOLLOW_MONEY_BETA_STORE_PASSWORD = $password
    $env:FOLLOW_MONEY_BETA_KEY_ALIAS = 'follow-money-beta'
    $env:FOLLOW_MONEY_BETA_KEY_PASSWORD = $password
    & .\gradlew.bat --offline --no-daemon assembleBeta lintBeta
    if ($LASTEXITCODE -ne 0) { throw 'Beta build or lint failed.' }
    $apk = Join-Path $root 'app\build\outputs\apk\beta\app-beta.apk'
    & $apksigner verify --verbose $apk
    if ($LASTEXITCODE -ne 0) { throw 'Beta APK signature verification failed.' }
    $archive = [IO.Compression.ZipFile]::OpenRead($apk)
    try {
        $entry = $archive.GetEntry('META-INF/version-control-info.textproto')
        if (-not $entry) { throw 'APK source revision metadata missing.' }
        $reader = [IO.StreamReader]::new($entry.Open())
        try { $revisionInfo = $reader.ReadToEnd() } finally { $reader.Dispose() }
    } finally { $archive.Dispose() }
    if ($revisionInfo -notmatch ('revision:\s*"' + [regex]::Escape($sourceCommit) + '"')) { throw 'APK embedded revision does not match source commit.' }
    if ((& git rev-parse HEAD).Trim() -ne $sourceCommit) { throw 'Source commit changed during the build.' }
    & git diff --quiet HEAD
    $sourceDirty = $sourceDirty -or $LASTEXITCODE -ne 0
    $output = Get-Content -LiteralPath (Join-Path (Split-Path $apk -Parent) 'output-metadata.json') -Raw | ConvertFrom-Json
    $receipt = [ordered]@{
        source_commit = $sourceCommit
        source_branch = $sourceBranch
        source_dirty = $sourceDirty
        version_code = $output.elements[0].versionCode
        version_name = $output.elements[0].versionName
        apk_sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
        built_at_utc = [DateTime]::UtcNow.ToString('o')
    }
    $receipt | ConvertTo-Json | Set-Content -LiteralPath (Join-Path (Split-Path $apk -Parent) 'app-beta.provenance.json') -Encoding utf8
    if ($sourceDirty) { Write-Warning 'Source has uncommitted changes; this commit alone does not identify the full build source.' }
    Write-Output "Signed Beta APK: $apk"
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name]) }
    $password = $null
    Pop-Location
}
