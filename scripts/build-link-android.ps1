# Cross-compiles the Transferd link (polyglot-app/link) for Android arm64 and copies the shared
# library into the app's jniLibs, where Gradle packages it. Needs: rustup target
# aarch64-linux-android, the Android NDK (ANDROID_NDK_HOME or the SDK's ndk folder), and the
# sibling Transferd checkout (see polyglot-app/link/Cargo.toml).
param([string]$Ndk = $env:ANDROID_NDK_HOME)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Ndk) {
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
    $Ndk = (Get-ChildItem (Join-Path $sdk 'ndk') -Directory | Sort-Object Name -Descending | Select-Object -First 1).FullName
}
if (-not $Ndk -or -not (Test-Path $Ndk)) { throw 'Android NDK not found; set ANDROID_NDK_HOME.' }
$bin = Join-Path $Ndk 'toolchains\llvm\prebuilt\windows-x86_64\bin'
$clang = Join-Path $bin 'aarch64-linux-android26-clang.cmd'
$env:CC_aarch64_linux_android = $clang
$env:AR_aarch64_linux_android = Join-Path $bin 'llvm-ar.exe'
$env:CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER = $clang
Push-Location (Join-Path $root 'polyglot-app\link')
try {
    cargo build --release --lib --target aarch64-linux-android
    if ($LASTEXITCODE) { throw 'cargo build failed' }
} finally { Pop-Location }
$dest = Join-Path $root 'SpendingSaverAndroid\app\src\main\jniLibs\arm64-v8a'
New-Item -ItemType Directory -Force $dest | Out-Null
Copy-Item (Join-Path $root 'polyglot-app\link\target\aarch64-linux-android\release\libss_link.so') $dest -Force
Write-Host "Copied libss_link.so to $dest"
