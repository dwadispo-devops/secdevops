param([string]$Chrome = 'C:/Program Files/Google/Chrome/Application/chrome.exe')
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath $Chrome)) { throw 'Chrome installe requis pour ce test navigateur.' }
$profile = Join-Path $root '.us10/chrome-profile'
$process = Start-Process -FilePath $Chrome -WindowStyle Hidden -PassThru -ArgumentList @(
    '--headless', '--disable-gpu', '--no-first-run', '--no-default-browser-check',
    '--remote-debugging-address=127.0.0.1', '--remote-debugging-port=29223',
    ('--user-data-dir="' + $profile + '"'), 'about:blank'
)
try {
    $ready = $false
    for ($i=0; $i -lt 30; $i++) {
        try { $ready = (Invoke-WebRequest -UseBasicParsing 'http://127.0.0.1:29223/json/version' -TimeoutSec 2).StatusCode -eq 200 } catch { }
        if ($ready) { break }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Chrome DevTools indisponible.' }
    & node (Join-Path $PSScriptRoot 'test-us10-browser.cjs')
    if ($LASTEXITCODE -ne 0) { throw 'Echec du test navigateur US10.' }
} finally { if (!$process.HasExited) { Stop-Process -Id $process.Id -ErrorAction SilentlyContinue } }
