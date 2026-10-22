param([string]$SigningDirectory = "$env:LOCALAPPDATA\FollowMoney\beta-signing")
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to a JDK with keytool.' }
$SigningDirectory = [IO.Path]::GetFullPath($SigningDirectory)
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
if ($SigningDirectory -eq $projectRoot -or $SigningDirectory.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Keep signing material outside the source checkout.' }
$keystore = Join-Path $SigningDirectory 'follow-money-beta.p12'
$passwordFile = Join-Path $SigningDirectory 'password.dpapi.xml'
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $passwordFile)) { throw 'Beta signing material already exists. Never replace it to build an update.' }
New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
# Restrict the folder to this Windows user with .NET directly: Set-Acl on Windows PowerShell 5.1 also tries to
# write the audit section and fails without SeSecurityPrivilege.
$directory = Get-Item -LiteralPath $SigningDirectory
$acl = $directory.GetAccessControl('Access')
$acl.SetAccessRuleProtection($true,$false)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$rule = New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
$acl.SetAccessRule($rule)
$directory.SetAccessControl($acl)
$previousPassword = $env:FOLLOW_MONEY_BETA_CREATION_PASSWORD
try {
    # Windows PowerShell 5.1 has no static RandomNumberGenerator.GetBytes(int); the instance form works in 5.1 and 7.
    $randomBytes = New-Object byte[] 32
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($randomBytes)
    $password = [Convert]::ToBase64String($randomBytes)
    ConvertTo-SecureString -String $password -AsPlainText -Force | Export-Clixml -LiteralPath $passwordFile
    $env:FOLLOW_MONEY_BETA_CREATION_PASSWORD = $password
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias follow-money-beta -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Mohamed Gamal, O=Follow Money Beta' -storepass:env FOLLOW_MONEY_BETA_CREATION_PASSWORD -keypass:env FOLLOW_MONEY_BETA_CREATION_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Beta key creation failed. Preserve the password file and inspect locally before retrying.' }
    & $keytool -exportcert -rfc -keystore $keystore -alias follow-money-beta -storepass:env FOLLOW_MONEY_BETA_CREATION_PASSWORD -file (Join-Path $SigningDirectory 'beta-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Beta certificate export failed.' }
    Write-Output "Beta key created in $SigningDirectory. Back it up and arrange password recovery before distributing updates."
} finally {
    $env:FOLLOW_MONEY_BETA_CREATION_PASSWORD = $previousPassword
    $password = $null
}
