[CmdletBinding()]
param([string]$ToolRoot = 'D:\codex-tools\linzin-android')

$ErrorActionPreference = 'Stop'
$ToolRoot = [IO.Path]::GetFullPath($ToolRoot)
if (-not $ToolRoot.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Install the Android toolchain on D:.'
}
$downloadRoot = Join-Path $ToolRoot 'downloads'
foreach ($dir in @($ToolRoot, $downloadRoot, (Join-Path $ToolRoot 'tmp'), (Join-Path $ToolRoot 'android-user-home'))) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
}
$env:TEMP = Join-Path $ToolRoot 'tmp'
$env:TMP = $env:TEMP
$env:ANDROID_USER_HOME = Join-Path $ToolRoot 'android-user-home'

function Get-VerifiedArchive {
    param([string]$Url, [string]$Name, [string]$Algorithm, [string]$Digest)
    $archive = Join-Path $downloadRoot $Name
    if (-not (Test-Path -LiteralPath $archive)) {
        & curl.exe --fail --location --retry 2 --output $archive $Url
        if ($LASTEXITCODE -ne 0) { throw "Download failed: $Name" }
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm $Algorithm).Hash -ne $Digest) {
        throw "Checksum mismatch: $Name. Inspect the cached archive before retrying."
    }
    return $archive
}

$jdkArchive = Get-VerifiedArchive `
    -Url 'https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip' `
    -Name 'OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip' `
    -Algorithm SHA256 -Digest '636bdc13dc649ddc7d5921c5c7d23cca6e44fd19d662b45cc9c02135a6c96c42'
if (-not (Test-Path -LiteralPath (Join-Path $ToolRoot 'jdk-25.0.5+8\bin\java.exe'))) {
    & tar.exe -xf $jdkArchive -C $ToolRoot
    if ($LASTEXITCODE -ne 0) { throw 'JDK extraction failed.' }
}

$cliArchive = Get-VerifiedArchive `
    -Url 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip' `
    -Name 'commandlinetools-win-16111833_latest.zip' `
    -Algorithm SHA1 -Digest '57d04f2d75eb8e8fffc5000a987e5de4b5a63e9d'
$cliRoot = Join-Path $ToolRoot 'sdk\cmdline-tools\23.0'
if (-not (Test-Path -LiteralPath (Join-Path $cliRoot 'bin\android.exe'))) {
    $staging = Join-Path $downloadRoot ('commandline-tools-' + [Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $staging, $cliRoot | Out-Null
    & tar.exe -xf $cliArchive -C $staging
    if ($LASTEXITCODE -ne 0) { throw 'Android command-line tools extraction failed.' }
    Get-ChildItem -LiteralPath (Join-Path $staging 'cmdline-tools') -Force | Copy-Item -Destination $cliRoot -Recurse
}
. (Join-Path $PSScriptRoot 'Set-AndroidEnvironment.ps1') -ToolRoot $ToolRoot

# Command-line Tools 23 uses Android CLI. The old Windows sdkmanager shim
# incorrectly splits semicolon-separated package identifiers in this environment.
& (Join-Path $cliRoot 'bin\android.exe') --no-metrics "--sdk=$env:ANDROID_HOME" sdk install 'platforms;android-37.0' 'build-tools;36.0.0' 'platform-tools'
if ($LASTEXITCODE -ne 0) { throw 'Android SDK package installation failed.' }
foreach ($required in @('platforms\android-37.0\android.jar', 'build-tools\36.0.0\aapt2.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME $required))) {
        throw "Android SDK package missing after installation: $required"
    }
}
& (Join-Path $env:JAVA_HOME 'bin\java.exe') -version
& (Join-Path $cliRoot 'bin\android.exe') --no-metrics "--sdk=$env:ANDROID_HOME" sdk list
if ($LASTEXITCODE -ne 0) { throw 'Android SDK verification failed.' }
Write-Host 'Android toolchain ready. Run scripts/build-android.ps1.'
