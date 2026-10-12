param([string]$SigningDirectory = "$env:LOCALAPPDATA\FollowMoney\signing", [switch]$Online)
$ErrorActionPreference = 'Stop'
$keystore = Join-Path $SigningDirectory 'follow-money-upload.p12'
$passwordFile = Join-Path $SigningDirectory 'password.dpapi.xml'
if (-not (Test-Path -LiteralPath $keystore) -or -not (Test-Path -LiteralPath $passwordFile)) { throw 'Local upload key/password not found. Run create-upload-key.ps1 only for a first publication.' }
$names = @('FOLLOW_MONEY_UPLOAD_KEYSTORE','FOLLOW_MONEY_UPLOAD_STORE_PASSWORD','FOLLOW_MONEY_UPLOAD_KEY_ALIAS','FOLLOW_MONEY_UPLOAD_KEY_PASSWORD')
$previous = @{}
foreach ($name in $names) { $previous[$name] = [Environment]::GetEnvironmentVariable($name) }
try {
    $securePassword = Import-Clixml -LiteralPath $passwordFile
    if ($securePassword -isnot [Security.SecureString]) { throw 'Expected a Windows DPAPI SecureString password file.' }
    $password = [Net.NetworkCredential]::new('',$securePassword).Password
    $env:FOLLOW_MONEY_UPLOAD_KEYSTORE = $keystore
    $env:FOLLOW_MONEY_UPLOAD_STORE_PASSWORD = $password
    $env:FOLLOW_MONEY_UPLOAD_KEY_ALIAS = 'follow-money-upload'
    $env:FOLLOW_MONEY_UPLOAD_KEY_PASSWORD = $password
    & "$PSScriptRoot\build-release.ps1" -Online:$Online
} finally {
    foreach ($name in $names) { [Environment]::SetEnvironmentVariable($name,$previous[$name]) }
    $password = $null
}
