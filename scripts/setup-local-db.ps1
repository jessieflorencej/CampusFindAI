param([ValidateRange(1, 65535)][int]$Port = 3307, [string]$MySqlBin = "")
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$localDir = Join-Path $projectRoot '.local\mysql'
$dataDir = Join-Path $localDir 'data'
$serverConfig = Join-Path $localDir 'my.ini'
$clientConfig = Join-Path $localDir 'client.ini'
$settingsPath = Join-Path $localDir 'settings.json'
$appConfig = Join-Path $projectRoot 'config\local.properties'

if (Test-Path -LiteralPath $settingsPath) {
    $settings = Get-Content -LiteralPath $settingsPath -Raw | ConvertFrom-Json
    $MySqlBin = $settings.MySqlBin
    $Port = $settings.Port
    foreach ($requiredConfig in @($serverConfig, $clientConfig, $appConfig)) {
        if (-not (Test-Path -LiteralPath $requiredConfig)) {
            throw "Saved project database configuration is incomplete: $requiredConfig is missing. Restore that file before restarting; existing database files have been preserved."
        }
    }
} elseif (-not $MySqlBin) {
    $mysqlCommand = Get-Command mysql.exe -ErrorAction SilentlyContinue
    if ($mysqlCommand) { $MySqlBin = Split-Path $mysqlCommand.Source -Parent }
    else { $MySqlBin = 'C:\Program Files\MySQL\MySQL Server 8.0\bin' }
}
$server = Join-Path $MySqlBin 'mysqld.exe'
$client = Join-Path $MySqlBin 'mysql.exe'
$admin = Join-Path $MySqlBin 'mysqladmin.exe'
if (-not (Test-Path -LiteralPath $server)) { throw "MySQL Server not found. Rerun with -MySqlBin 'path\to\mysql\bin'." }
$mysqlBase = Split-Path $MySqlBin -Parent

function Test-ProjectDatabase {
    if (-not (Test-Path -LiteralPath $clientConfig)) { return $false }
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        # Explicit endpoint arguments override any machine-wide MySQL login options.
        $result = & $client "--defaults-file=$clientConfig" '--host=127.0.0.1' "--port=$Port" '--protocol=tcp' '--user=root' '--connect-timeout=2' '--batch' '--skip-column-names' '--execute=SELECT @@datadir FROM information_schema.schemata WHERE schema_name=''campus_lost_found''' 2>$null
        if ($LASTEXITCODE -ne 0 -or -not $result) { return $false }
        $actualDataDir = [System.IO.Path]::GetFullPath(([string]$result).Trim()).TrimEnd('\', '/')
        $expectedDataDir = [System.IO.Path]::GetFullPath($dataDir).TrimEnd('\', '/')
        return $actualDataDir -ieq $expectedDataDir
    } finally { $ErrorActionPreference = $previousPreference }
}
function New-LocalSecret {
    $bytes = New-Object byte[] 24
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
function Write-Utf8([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, [System.Text.UTF8Encoding]::new($false))
}
if (Test-ProjectDatabase) {
    $completedInit = Join-Path $localDir 'first-start.sql'
    if (Test-Path -LiteralPath $completedInit) { Remove-Item -LiteralPath $completedInit }
    Write-Host "Project MySQL is already running on localhost:$Port."
    exit 0
}

# Check only the dedicated project port; never stop or reconfigure another server.
$socket = New-Object System.Net.Sockets.TcpClient
try {
    $socket.Connect('127.0.0.1', $Port)
    throw "Port $Port is already in use by a different database. Choose a free port with -Port."
} catch [System.Net.Sockets.SocketException] {
    # Expected: our dedicated port is available.
} finally { $socket.Dispose() }

$firstRun = -not (Test-Path -LiteralPath $settingsPath)
if ($firstRun) {
    if (Test-Path -LiteralPath $dataDir) { throw "Unconfigured data directory already exists at $dataDir. Preserve it and inspect setup before retrying." }
    if (Test-Path -LiteralPath $appConfig) { throw "config/local.properties already exists. This script will not overwrite an existing database configuration." }
    New-Item -ItemType Directory -Path $localDir -Force | Out-Null
    New-Item -ItemType Directory -Path (Split-Path $appConfig -Parent) -Force | Out-Null
    $rootSecret = New-LocalSecret
    $appSecret = New-LocalSecret
    $dataSqlPath = $dataDir.Replace('\','/')
    $baseSqlPath = $mysqlBase.Replace('\','/')
    $logSqlPath = (Join-Path $localDir 'mysql.log').Replace('\','/')
    $pidSqlPath = (Join-Path $localDir 'mysql.pid').Replace('\','/')
    Write-Utf8 $serverConfig "[mysqld]`nbasedir=$baseSqlPath`ndatadir=$dataSqlPath`nport=$Port`nbind-address=127.0.0.1`nmysqlx=0`nlog-error=$logSqlPath`npid-file=$pidSqlPath`ncharacter-set-server=utf8mb4`ncollation-server=utf8mb4_unicode_ci`n"
    Write-Utf8 $clientConfig "[client]`nuser=root`npassword=$rootSecret`nhost=127.0.0.1`nport=$Port`nprotocol=tcp`n"
    $initFile = Join-Path $localDir 'first-start.sql'
    Write-Utf8 $initFile "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootSecret';`nCREATE DATABASE IF NOT EXISTS campus_lost_found CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`nCREATE USER IF NOT EXISTS 'campus_app'@'localhost' IDENTIFIED BY '$appSecret';`nGRANT ALL PRIVILEGES ON campus_lost_found.* TO 'campus_app'@'localhost';`n"
    Write-Host 'Initializing a separate MySQL data directory for this project...'
    & $server '--no-defaults' '--initialize-insecure' "--basedir=$mysqlBase" "--datadir=$dataDir" '--console'
    if ($LASTEXITCODE -ne 0) { throw 'MySQL initialization failed. Check the output; no existing MySQL service was modified.' }
    Write-Utf8 $appConfig "spring.datasource.url=jdbc:mysql://127.0.0.1:$Port/campus_lost_found?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`nspring.datasource.username=campus_app`nspring.datasource.password=$appSecret`n"
    Write-Utf8 $settingsPath (@{ MySqlBin = $MySqlBin; Port = $Port } | ConvertTo-Json)
}
$arguments = @("`"--defaults-file=$serverConfig`"")
$pendingInit = Join-Path $localDir 'first-start.sql'
if (Test-Path -LiteralPath $pendingInit) { $arguments += "`"--init-file=$pendingInit`"" }
$process = Start-Process -FilePath $server -ArgumentList $arguments -WindowStyle Hidden -PassThru
for ($attempt = 0; $attempt -lt 30; $attempt++) {
    if (Test-ProjectDatabase) {
        if (Test-Path -LiteralPath $pendingInit) { Remove-Item -LiteralPath $pendingInit }
        Write-Host "Project database campus_lost_found is ready on localhost:$Port."
        Write-Host 'Application credentials were saved in config/local.properties; keep that file private.'
        exit 0
    }
    if ($process.HasExited) { throw "Project MySQL stopped. Check $localDir\mysql.log." }
    Start-Sleep -Seconds 1
}
throw "MySQL did not become ready. Check $localDir\mysql.log."
