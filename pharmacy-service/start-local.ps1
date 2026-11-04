param([string]$Jar)

$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    if (!(Test-Path -LiteralPath '.env')) {
        throw 'Créer pharmacy-service/.env avec DB_URL, DB_USER, DB_PASSWORD et JWT_SECRET.'
    }
    foreach ($line in Get-Content -LiteralPath '.env') {
        if ($line -match '^\s*([A-Z][A-Z0-9_]*)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
    foreach ($name in 'DB_URL', 'DB_USER', 'DB_PASSWORD', 'JWT_SECRET') {
        if (![Environment]::GetEnvironmentVariable($name, 'Process')) {
            throw "Variable obligatoire absente : $name"
        }
    }
    if (!$env:DB_URL.StartsWith('jdbc:postgresql://')) {
        throw 'DB_URL doit commencer par jdbc:postgresql://'
    }
    if ($Jar) {
        & java -jar $Jar
    } else {
        & .\mvnw.cmd spring-boot:run
    }
    if ($LASTEXITCODE -ne 0) { throw "Démarrage échoué (code $LASTEXITCODE)." }
} finally {
    Pop-Location
}
