# Keep Maven switches verbatim; advanced PowerShell parameter binding would
# interpret switches such as -o as its own common parameters.
$MavenArgs = $args
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$mavenVersion = '3.9.11'
$toolDir = Join-Path $projectRoot '.tools'
$maven = Join-Path $toolDir "apache-maven-$mavenVersion\bin\mvn.cmd"
if (-not (Test-Path -LiteralPath $maven)) {
    New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
    $archive = Join-Path $toolDir "apache-maven-$mavenVersion-bin.zip"
    $download = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Write-Host 'Downloading Apache Maven for this project...'
    Invoke-WebRequest -UseBasicParsing -Uri $download -OutFile $archive
    $expected = (Invoke-RestMethod -Uri "$download.sha512").Trim()
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ine $expected) { throw 'Maven download checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $toolDir -Force
}
Push-Location $projectRoot
try {
    & $maven '-ntp' "-Dmaven.repo.local=$toolDir\m2" @MavenArgs
    exit $LASTEXITCODE
} finally { Pop-Location }
