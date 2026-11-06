$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$dir = Join-Path $root '.us10'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$file = Join-Path $dir 'demo.env'
if (Test-Path -LiteralPath $file) {
    Write-Host 'Configuration locale existante conservee dans .us10/demo.env.'
    return
}
function New-LocalSecret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    [Convert]::ToBase64String($bytes)
}
$lines = @(
    'DB_USER=us10_demo_owner'
    ('DB_PASSWORD=' + (New-LocalSecret))
    ('JWT_SECRET=' + (New-LocalSecret))
    ('PRESCRIPTION_IMAGE_KEY=' + (New-LocalSecret))
    'GEMINI_API_KEY=not-configured'
    'LOGGING_LEVEL_ROOT=INFO'
    'LOGGING_LEVEL_ORG_SPRINGFRAMEWORK=INFO'
)
[IO.File]::WriteAllLines($file, $lines)
Write-Host 'Secrets aleatoires crees dans .us10/demo.env (ignore par Git).'
Write-Host 'OCR reel facultatif : configurer GEMINI_API_KEY dans ce fichier pour appeler le fournisseur existant.'
