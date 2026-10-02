 $ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$settingsPath = Join-Path $projectRoot '.local\mysql\settings.json'
if (-not (Test-Path -LiteralPath $settingsPath)) { Write-Host 'No project database is configured.'; exit 0 }
$settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
$admin = Join-Path $settings.MySqlBin 'mysqladmin.exe'
$client = Join-Path $settings.MySqlBin 'mysql.exe'
$clientConfig = Join-Path $projectRoot '.local\mysql\client.ini'
$connectionArgs = @("--defaults-file=$clientConfig", '--host=127.0.0.1', "--port=$($settings.Port)", '--protocol=tcp', '--user=root')
# Verify the actual data directory before any shutdown request; never trust only a port.
$actualDataDir = & $client @connectionArgs '--connect-timeout=2' '--batch' '--skip-column-names' '--execute=SELECT @@datadir'
if ($LASTEXITCODE -ne 0 -or -not $actualDataDir) { throw 'Could not verify the project database. It may already be stopped.' }
$actualDataDir = [System.IO.Path]::GetFullPath(([string]$actualDataDir).Trim()).TrimEnd('\', '/')
$expectedDataDir = [System.IO.Path]::GetFullPath((Join-Path $projectRoot '.local\mysql\data')).TrimEnd('\', '/')
if ($actualDataDir -ine $expectedDataDir) { throw 'The database at the configured port belongs to another data directory. It has not been stopped.' }
& $admin @connectionArgs shutdown
if ($LASTEXITCODE -ne 0) { throw 'Could not stop the project database. It may already be stopped.' }
Write-Host 'Project MySQL stopped. Its data is preserved.'
