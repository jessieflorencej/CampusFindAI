$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$jar = [System.IO.Path]::GetFullPath((Join-Path $projectRoot 'target\CampusLostAndFound-1.0.jar'))
# Match this checkout's absolute JAR path before stopping any Java process.
$processes = Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object {
    $_.CommandLine -and $_.CommandLine.IndexOf($jar, [System.StringComparison]::OrdinalIgnoreCase) -ge 0
}
if (-not $processes) { Write-Host 'No background website process for this project was found. A manually launched Java process can be stopped with Ctrl+C in its terminal.'; exit 0 }
foreach ($process in $processes) {
    $result = Invoke-CimMethod -InputObject $process -MethodName Terminate
    if ($result.ReturnValue -ne 0) { throw "Could not stop the project process $($process.ProcessId)." }
}
Write-Host 'CampusFind AI stopped. Your saved records remain in MySQL.'
