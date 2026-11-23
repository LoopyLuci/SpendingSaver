<#
.SYNOPSIS
  Runs the project's local test suites (GitHub Actions is not available for this repo).
.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts/check.ps1                # everything
  powershell -ExecutionPolicy Bypass -File scripts/check.ps1 -Only backend  # backend | mcp | web | android
#>
param([ValidateSet('all', 'backend', 'mcp', 'web', 'android')][string]$Only = 'all')

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$venv = Join-Path $root '.venv-check'   # git-ignored; created on first run
$py = Join-Path $venv 'Scripts\python.exe'
$results = [ordered]@{}

function Step($name, [scriptblock]$body) {
    if ($Only -ne 'all' -and $Only -ne $name) { return }
    Write-Host "`n=== $name ===" -ForegroundColor Cyan
    try {
        $script:code = 0
        & $body
        if ($script:code -ne 0) { throw "exit code $script:code" }
        $results[$name] = 'PASS'
    } catch {
        Write-Host $_ -ForegroundColor Red
        $results[$name] = 'FAIL'
    }
}

function EnsureVenv {
    if (-not (Test-Path $py)) { python -m venv $venv }
    & $py -m pip install -q --disable-pip-version-check -e "$root/polyglot-app/backend[dev]" -e "$root/spendingsaver-mcp[test]"
    if ($LASTEXITCODE -ne 0) { throw "pip install failed ($LASTEXITCODE)" }
}

Step 'backend' {
    EnsureVenv
    Push-Location "$root\polyglot-app\backend"
    try { & $py -m pytest -q; $script:code = $LASTEXITCODE } finally { Pop-Location }
}
Step 'mcp' {
    EnsureVenv
    Push-Location "$root\spendingsaver-mcp"
    try { & $py -m pytest -q; $script:code = $LASTEXITCODE } finally { Pop-Location }
}
Step 'web' {
    Push-Location "$root\spendingsaver-web"
    try {
        if (-not (Test-Path node_modules)) { npm ci --silent }
        npm test --silent; $script:code = $LASTEXITCODE
    } finally { Pop-Location }
}
Step 'android' {
    Push-Location "$root\SpendingSaverAndroid"
    try { & .\gradlew.bat :app:testDebugUnitTest --console=plain; $script:code = $LASTEXITCODE } finally { Pop-Location }
}

Write-Host "`n=== summary ===" -ForegroundColor Cyan
$results.GetEnumerator() | ForEach-Object { '{0,-8} {1}' -f $_.Key, $_.Value }
if ($results.Values -contains 'FAIL') { exit 1 }
