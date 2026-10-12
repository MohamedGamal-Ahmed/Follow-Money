param([string]$SigningDirectory = "$env:LOCALAPPDATA\FollowMoney\signing")
$ErrorActionPreference = 'Stop'
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath "$env:JAVA_HOME\bin\javac.exe")) { $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr' }
$keytool = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
if (-not (Test-Path -LiteralPath $keytool)) { throw 'Set JAVA_HOME to a JDK with keytool.' }
$SigningDirectory = [IO.Path]::GetFullPath($SigningDirectory)
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
if ($SigningDirectory -eq $projectRoot -or $SigningDirectory.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Keep signing material outside the source checkout.' }
$keystore = Join-Path $SigningDirectory 'follow-money-upload.p12'
$passwordFile = Join-Path $SigningDirectory 'password.dpapi.xml'
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $passwordFile)) { throw 'Signing material already exists. Do not replace an upload key.' }
New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
$acl = Get-Acl -LiteralPath $SigningDirectory
$acl.SetAccessRuleProtection($true,$false)
$identity = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$rule = [Security.AccessControl.FileSystemAccessRule]::new($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
$acl.SetAccessRule($rule)
Set-Acl -LiteralPath $SigningDirectory -AclObject $acl
$previousPassword = $env:FOLLOW_MONEY_KEY_CREATION_PASSWORD
try {
    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $securePassword = ConvertTo-SecureString -String $password -AsPlainText -Force
    # Windows DPAPI: decryptable only by this Windows user on this machine.
    $securePassword | Export-Clixml -LiteralPath $passwordFile
    $env:FOLLOW_MONEY_KEY_CREATION_PASSWORD = $password
    & $keytool -genkeypair -keystore $keystore -storetype PKCS12 -alias follow-money-upload -keyalg RSA -keysize 4096 -validity 10000 -dname 'CN=Mohamed Gamal, O=Follow Money' -storepass:env FOLLOW_MONEY_KEY_CREATION_PASSWORD -keypass:env FOLLOW_MONEY_KEY_CREATION_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Upload key generation failed. Preserve the password file for recovery; inspect locally before retrying.' }
    & $keytool -exportcert -rfc -keystore $keystore -alias follow-money-upload -storepass:env FOLLOW_MONEY_KEY_CREATION_PASSWORD -file (Join-Path $SigningDirectory 'upload-certificate.pem')
    if ($LASTEXITCODE -ne 0) { throw 'Upload certificate export failed.' }
    Write-Output "Upload key created in $SigningDirectory. No password is printed. Back up the key and arrange portable password recovery before publishing."
} finally {
    $env:FOLLOW_MONEY_KEY_CREATION_PASSWORD = $previousPassword
    $password = $null
}
