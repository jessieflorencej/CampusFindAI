param([switch]$NoBrowser)
$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent))
$jar = Join-Path $projectRoot 'target\CampusLostAndFound-1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build first using .\mvnw.cmd clean verify.' }
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'setup-local-db.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Project database startup failed.' }
$port = if ($env:PORT) { $env:PORT } else { '8080' }
$url = "http://localhost:$port"
$processes = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains($jar) })
if ($processes.Count -eq 0) {
    $log = Join-Path $projectRoot '.local\app.log'
    $errorLog = Join-Path $projectRoot '.local\app.err.log'
    $running = Start-Process -FilePath 'java.exe' -ArgumentList @('-jar', "`"$jar`"") -WorkingDirectory $projectRoot -WindowStyle Hidden -RedirectStandardOutput $log -RedirectStandardError $errorLog -PassThru
    [IO.File]::WriteAllText((Join-Path $projectRoot '.local\app.pid'),[string]$running.Id)
}
$ready = $false
for ($attempt=0; $attempt -lt 30; $attempt++) {
    try { $response = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$port/api/stats" -TimeoutSec 5; if ($response.StatusCode -eq 200) { $ready = $true; break } } catch { }
    if ($running) { $running.Refresh(); if ($running.HasExited) { throw 'Java stopped during startup. Read .local/app.log.' } }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw 'Website did not start. Read .local/app.log and .local/app.err.log.' }
Write-Host "CampusFind AI is ready at $url. Double-click stop.cmd to stop it."
if (-not $NoBrowser) { Start-Process $url }
