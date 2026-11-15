param([string]$Apk = 'app/build/outputs/apk/release/app-release.apk')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Set-Location $root
$manifest = Get-Content tools/native/manifest.json -Raw | ConvertFrom-Json
foreach ($entry in $manifest.libraries.PSObject.Properties) {
    $actual = (Get-FileHash (Join-Path 'app/src/main/jniLibs' $entry.Name) -Algorithm SHA256).Hash.ToLower()
    if ($actual -ne $entry.Value) { throw "Native checksum mismatch: $($entry.Name)" }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $Apk))
try {
    foreach ($entry in $archive.Entries) {
        if ($entry.FullName -match '(?i)\.(p12|jks|keystore)$|signing.properties|local.properties|notification.*dump') { throw "Unexpected APK entry: $($entry.FullName)" }
        $stream = $entry.Open()
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8)
        try { $body = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($body -match 'C:[\\/]+Users[\\/]|/Users/|github_pat_[A-Za-z0-9_]{20,}|gh[pousr]_[A-Za-z0-9]{20,}|-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----') {
            throw "Sensitive pattern found in APK entry: $($entry.FullName)"
        }
    }
    $abis = $archive.Entries.FullName | Where-Object {$_ -match '^lib/[^/]+/lib(gg|xp)\.so$'}
    if ($abis.Count -ne 6) { throw 'Missing native ABI libraries' }
    Write-Output 'Native hashes and APK content checks passed; 3 ABIs, 6 transport libraries.'
} finally { $archive.Dispose() }
Get-FileHash $Apk -Algorithm SHA256 | Select-Object Algorithm,Hash
