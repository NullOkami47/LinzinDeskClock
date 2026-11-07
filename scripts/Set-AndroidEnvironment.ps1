[CmdletBinding()]
param([string]$ToolRoot = 'D:\codex-tools\linzin-android')

$ErrorActionPreference = 'Stop'
$ToolRoot = [IO.Path]::GetFullPath($ToolRoot)
if (-not $ToolRoot.StartsWith('D:\', [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Keep the Android toolchain and caches on D:.'
}
$jdk = Join-Path $ToolRoot 'jdk-25.0.5+8'
if (-not (Test-Path -LiteralPath (Join-Path $jdk 'bin\java.exe'))) {
    throw 'JDK 25.0.5+8 is missing. Run scripts/install-android-toolchain.ps1 first.'
}
foreach ($folder in @('tmp', 'home', 'gradle-user-home', 'android-user-home', 'sdk')) {
    New-Item -ItemType Directory -Force -Path (Join-Path $ToolRoot $folder) | Out-Null
}
$env:JAVA_HOME = $jdk
$env:ANDROID_HOME = Join-Path $ToolRoot 'sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $ToolRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $ToolRoot 'gradle-user-home'
$env:TEMP = Join-Path $ToolRoot 'tmp'
$env:TMP = $env:TEMP
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir="{0}" -Duser.home="{1}"' -f $env:TEMP.Replace('\', '/'), (Join-Path $ToolRoot 'home').Replace('\', '/')
$env:Path = "$jdk\bin;$env:ANDROID_HOME\cmdline-tools\23.0\bin;$env:ANDROID_HOME\platform-tools;$env:Path"
