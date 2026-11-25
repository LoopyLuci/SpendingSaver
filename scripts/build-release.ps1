# Builds the two release files into dist/: the Windows installer and the signed Android APK, plus checksums.
# Needs Rust, NSIS (makensis) and, for a signed APK, SpendingSaverAndroid/keystore.properties.
param([string]$Version = '1.1.2')
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force $dist | Out-Null

Push-Location (Join-Path $root 'spendingsaver-desktop\desktop')
cargo build --release
if ($LASTEXITCODE -ne 0) { throw 'cargo build failed' }
Pop-Location
$exe = Join-Path $root 'spendingsaver-desktop\desktop\target\release\spendingsaver.exe'
# The optional "Connect from anywhere" agent (Transferd). Built from the link crate; fetching Transferd needs network.
Push-Location (Join-Path $root 'spendingsaver-desktop\link')
cargo build --release --bin ss-link-agent
if ($LASTEXITCODE -ne 0) { throw 'link agent build failed' }
Pop-Location
$agent = Join-Path $root 'spendingsaver-desktop\link\target\release\ss-link-agent.exe'
$nsis = 'C:\Program Files (x86)\NSIS\makensis.exe'
& $nsis /V2 "/DVERSION=$Version" "/DEXE=$exe" "/DAGENT=$agent" "/DOUT=$dist\SpendingSaver-Setup-$Version.exe" (Join-Path $root 'spendingsaver-desktop\desktop\installer\spendingsaver.nsi')
if ($LASTEXITCODE -ne 0) { throw 'makensis failed' }

Push-Location (Join-Path $root 'SpendingSaverAndroid')
foreach ($flavor in 'universal', 'legacy') {
  $cap = (Get-Culture).TextInfo.ToTitleCase($flavor)
  $apk = Join-Path $root "SpendingSaverAndroid\app\build\outputs\apk\$flavor\release\app-$flavor-release.apk"
  Remove-Item $apk -ErrorAction SilentlyContinue   # never ship a stale file
  .\gradlew.bat --no-daemon ":app:assemble${cap}Release" --console=plain
  if ($LASTEXITCODE -ne 0) { throw "gradle failed ($flavor)" }
  Copy-Item -ErrorAction Stop $apk "$dist\SpendingSaver-$Version-$flavor.apk" -Force
}
Pop-Location

Get-ChildItem $dist -File | Where-Object { $_.Name -ne 'SHA256SUMS.txt' } | ForEach-Object {
  "{0}  {1}" -f (Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLower(), $_.Name
} | Set-Content (Join-Path $dist 'SHA256SUMS.txt')
Get-ChildItem $dist | Select-Object Name, Length
