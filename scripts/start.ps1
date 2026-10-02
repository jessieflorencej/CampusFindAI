param([switch]$UseExistingDatabase, [switch]$Rebuild)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    if (-not $UseExistingDatabase) {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'setup-local-db.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'Database startup failed.' }
    }
    $jar = Join-Path $projectRoot 'target\CampusLostAndFound-1.0.jar'
    if ($Rebuild -or -not (Test-Path -LiteralPath $jar)) {
        & (Join-Path $projectRoot 'mvnw.cmd') '-DskipTests' 'package'
        if ($LASTEXITCODE -ne 0) { throw 'Java build failed.' }
    }
    $webPort = if ($env:PORT) { $env:PORT } else { '8080' }
    Write-Host "Open http://localhost:$webPort. Press Ctrl+C here to stop the website."
    & java '-jar' $jar
    if ($LASTEXITCODE -ne 0) { throw 'The Java application stopped with an error.' }
} finally { Pop-Location }
