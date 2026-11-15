param()
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$private = Join-Path $root '.private'
New-Item -ItemType Directory -Force $private | Out-Null
$props = Join-Path $private 'signing.properties'
$key = Join-Path $private 'airnoti-release.p12'
if ((Test-Path $props) -or (Test-Path $key)) { throw 'Signing material already exists; never overwrite it.' }
$bytes = [byte[]]::new(32)
[Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
$password = [Convert]::ToBase64String($bytes)
$env:AIRNOTI_KEY_PASSWORD = $password
try {
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $key -storetype PKCS12 -alias airnoti -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=AirNoti Release' -storepass:env AIRNOTI_KEY_PASSWORD -keypass:env AIRNOTI_KEY_PASSWORD
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed' }
    @("storeFile=.private/airnoti-release.p12", "storePassword=$password", 'keyAlias=airnoti', "keyPassword=$password") | Set-Content -LiteralPath $props
} finally { Remove-Item Env:AIRNOTI_KEY_PASSWORD -ErrorAction SilentlyContinue; $password = $null }
Write-Output 'Signing key created under .private. Back up both files securely; they are excluded from Git.'
