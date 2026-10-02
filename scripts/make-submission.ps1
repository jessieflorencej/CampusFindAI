$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $projectRoot 'target\CampusLostAndFound-1.0.jar'
if (-not (Test-Path -LiteralPath $jar)) { throw 'Build the project first with .\mvnw.cmd clean verify.' }
$jarModified = (Get-Item -LiteralPath $jar).LastWriteTimeUtc
$runtimeSources = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'src\main') -Recurse -File)
$runtimeSources += Get-Item -LiteralPath (Join-Path $projectRoot 'pom.xml')
if ($runtimeSources | Where-Object { $_.LastWriteTimeUtc -gt $jarModified } | Select-Object -First 1) {
    throw 'The source has changed since the JAR was built. Run .\mvnw.cmd clean verify before creating the submission.'
}
$outputDir = Join-Path $projectRoot 'submission'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
$archivePath = Join-Path $outputDir 'CampusFindAI-Submission.zip'

# Explicit allowlist: never bundle local database passwords, data, caches or logs.
$files = @()
foreach ($directory in @('src', 'sql', 'documentation', 'scripts')) {
    $files += Get-ChildItem -LiteralPath (Join-Path $projectRoot $directory) -Recurse -File
}
foreach ($relative in @('pom.xml', 'README.md', '.gitignore', 'mvnw.cmd', 'start.cmd', 'open-campusfind.cmd', 'stop.cmd', 'config\local.properties.example', 'target\CampusLostAndFound-1.0.jar')) {
    $files += Get-Item -LiteralPath (Join-Path $projectRoot $relative)
}
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$stream = [System.IO.File]::Open($archivePath, [System.IO.FileMode]::Create)
$archive = [System.IO.Compression.ZipArchive]::new($stream, [System.IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace('\', '/')
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($archive, $file.FullName, "CampusLostAndFound/$relative", [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally { $archive.Dispose(); $stream.Dispose() }
Write-Host "Submission created: $archivePath"
Write-Host 'Source, executable JAR, SQL, tests, and documentation included. Local credentials and databases excluded.'
