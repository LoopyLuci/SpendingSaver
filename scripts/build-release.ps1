# Builds the two release files into dist/: the Windows installer and the signed Android APK, plus checksums.
# Needs Rust, NSIS (makensis) and, for a signed APK, SpendingSaverAndroid/keystore.properties.
param([string]$Version = '1.1.0')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null

Push-Location (Join-Path $root 'polyglot-app\desktop')
cargo build --release
if ($LASTEXITCODE -ne 0) { throw 'cargo build failed' }
Pop-Location
$exe = Join-Path $root 'polyglot-app\desktop\target\release\spendingsaver.exe'
$nsis = 'C:\Program Files (x86)\NSIS\makensis.exe'
& $nsis /V2 "/DVERSION=$Version" "/DEXE=$exe" "/DOUT=$dist\SpendingSaver-Setup-$Version.exe" (Join-Path $root 'polyglot-app\desktop\installer\spendingsaver.nsi')
if ($LASTEXITCODE -ne 0) { throw 'makensis failed' }

$apk = Join-Path $root 'SpendingSaverAndroid\app\build\outputs\apk\release\app-release.apk'
Remove-Item $apk -ErrorAction SilentlyContinue   # never ship a stale file
Push-Location (Join-Path $root 'SpendingSaverAndroid')
.\gradlew.bat --no-daemon :app:assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw 'gradle failed' }
Pop-Location
Copy-Item -ErrorAction Stop $apk "$dist\SpendingSaver-$Version.apk" -Force

Get-ChildItem $dist -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } | ForEach-Object {
  "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
} | Set-Content (Join-Path $dist 'SHA256SUMS.txt')
Get-ChildItem $dist | Select-Object Name, Length
