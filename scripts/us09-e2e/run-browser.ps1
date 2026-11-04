param(
    [string]$Chrome = 'C:\Program Files\Google\Chrome\Application\chrome.exe'
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$evidence = Join-Path $root 'pharmacy-service/target/devops87-evidence'
New-Item -ItemType Directory -Force $evidence | Out-Null
if (!(Test-Path -LiteralPath $Chrome)) { throw 'Chrome executable not found.' }
# This script never opens an interactive demonstration window.
$ready = $false
try { $ready = (Invoke-WebRequest 'http://127.0.0.1:29222/json/version' -UseBasicParsing -TimeoutSec 2).StatusCode -eq 200 } catch { }
if (!$ready) {
    $profile = Join-Path $evidence 'chrome-profile'
    Start-Process -FilePath $Chrome -WindowStyle Hidden -ArgumentList @(
        '--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
        '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=29222',
        ('--user-data-dir="' + $profile + '"'), 'about:blank'
    ) -RedirectStandardOutput (Join-Path $evidence 'chrome.log') `
      -RedirectStandardError (Join-Path $evidence 'chrome-error.log') | Out-Null
}
& node (Join-Path $PSScriptRoot 'browser.cjs')
if ($LASTEXITCODE -ne 0) { throw 'US09 browser validation failed; inspect the redacted E2E report.' }
