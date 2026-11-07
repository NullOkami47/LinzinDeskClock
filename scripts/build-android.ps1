[CmdletBinding()]
param(
    [string[]]$Tasks = @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebugAndroidTest'),
    [string]$ToolRoot = 'D:\codex-tools\linzin-android',
    [string]$LogPath
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'Set-AndroidEnvironment.ps1') -ToolRoot $ToolRoot
if (-not $LogPath) {
    $LogPath = Join-Path $projectRoot ('.local\build-logs\build-{0}.log' -f (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$LogPath = [IO.Path]::GetFullPath($LogPath)
if (-not $LogPath.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep build logs on D:.'
}
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $LogPath) | Out-Null
Push-Location $projectRoot
try {
    # Continue independent checks to capture all useful diagnostics while features are edited.
    $ErrorActionPreference = 'Continue'
    & (Join-Path $projectRoot 'gradlew.bat') @Tasks --console=plain --no-daemon --continue 2>&1 | Tee-Object -FilePath $LogPath
    $buildExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
Write-Host "Build log: $LogPath"
exit $buildExitCode
