[CmdletBinding()]
param([switch]$PreflightOnly, [string]$ExpectedBranch = 'feat/desk-clock-v2')

$ErrorActionPreference = 'Stop'
$qaRoot = 'D:\codex-tools\linzin-android\runner-QA'
$toolRoot = Join-Path $qaRoot 'toolchain'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = 'D:\codex-tools\linzin-android\sdk\platform-tools\adb.exe'
$reportRoot = if ($PreflightOnly) { Join-Path $qaRoot 'evidence\preflight' } else { Join-Path $projectRoot '.local\self-hosted-validation' }
if ($ExpectedBranch -cne 'feat/desk-clock-v2') { throw 'Only the reviewed feature branch is allowed.' }
if (-not $PreflightOnly) {
    if ($env:GITHUB_REPOSITORY -cne 'NullOkami47/LinzinDeskClock' -or
        $env:GITHUB_REF -cne 'refs/heads/feat/desk-clock-v2' -or
        $env:GITHUB_ACTOR -cne 'NullOkami47' -or
        $env:GITHUB_EVENT_NAME -notin @('push', 'workflow_dispatch')) {
        throw 'Repository, branch, actor or event guard failed.'
    }
    $allowedWork = [IO.Path]::GetFullPath((Join-Path $qaRoot 'work')) + '\'
    if (-not [IO.Path]::GetFullPath($projectRoot).StartsWith($allowedWork, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Use the isolated QA runner checkout.'
    }
    $head = (& git -C $projectRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -cne $env:GITHUB_SHA) { throw 'Checkout does not match the triggering commit.' }
}
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$run = Get-Content -LiteralPath 'D:\codex-tools\linzin-android\emulator-qa\current-run.json' -Raw | ConvertFrom-Json
if ($run.AvdName -cne 'Linzin_API35_GoogleApis_QA' -or $run.Serial -cne 'emulator-5580' -or
    $run.AdbHost -cne '127.0.0.1' -or $run.AdbServerPort -ne 5038) { throw 'Dedicated emulator record mismatch.' }

function Assert-PrivateAdbServer {
    $server = Get-Process -Id $run.AdbProcessId -ErrorAction Stop
    if ($server.Path -ne $adb -or $server.StartTime -ne [datetime]$run.AdbStartTime) {
        throw 'Recorded private adb process exited or was replaced; refusing to start another server.'
    }
    $serverInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($run.AdbProcessId)"
    if ($serverInfo.CommandLine -notmatch '--one-device\s+emulator-5580(?:\s|$)' -or
        $serverInfo.CommandLine -notmatch '-L\s+tcp:5038(?:\s|$)') { throw 'Private adb server isolation mismatch.' }
    $qemu = Get-Process -Id $run.QemuProcessId -ErrorAction Stop
    if ($qemu.ProcessName -notlike 'qemu-system-x86_64*') { throw 'Recorded QEMU process mismatch.' }
    $qemuInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($run.QemuProcessId)"
    if ($qemuInfo.CommandLine -notmatch 'Linzin_API35_GoogleApis_QA' -or
        $qemuInfo.CommandLine -notmatch '-port\s+5580(?:\s|$)') { throw 'QEMU AVD/port mismatch.' }
    $socket = [Net.Sockets.TcpClient]::new()
    try {
        $connection = $socket.ConnectAsync('127.0.0.1', 5038)
        if (-not $connection.Wait(2000) -or -not $socket.Connected) { throw 'Private adb endpoint unavailable.' }
    } finally { $socket.Dispose() }
}

function Invoke-DedicatedAdb {
    param([string[]]$Arguments, [int]$TimeoutSeconds = 15)
    Assert-PrivateAdbServer
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $adb
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    foreach ($argument in (@('-H', '127.0.0.1', '-P', '5038', '-s', 'emulator-5580') + $Arguments)) {
        $info.ArgumentList.Add($argument)
    }
    $process = [Diagnostics.Process]::Start($info)
    try {
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            # Stop only our adb client; never stop a server, emulator, or another command.
            $process.Kill()
            $process.WaitForExit()
            $stdout.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $reportRoot 'timed-out-adb-stdout.txt')
            throw 'Dedicated adb timed out. Device-side work may still run; inspect before retrying.'
        }
        $text = $stdout.GetAwaiter().GetResult()
        $errorText = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) { throw "Dedicated adb failed ($($process.ExitCode)): $errorText" }
        return $text.Trim()
    } finally { $process.Dispose() }
}

function Assert-DedicatedEmulator {
    if ((Invoke-DedicatedAdb -Arguments @('get-state')) -cne 'device') { throw 'Dedicated emulator is offline.' }
    $avd = ((Invoke-DedicatedAdb -Arguments @('emu', 'avd', 'name')) -split '[\r\n]+')[0].Trim()
    if ($avd -cne 'Linzin_API35_GoogleApis_QA') { throw 'AVD identity mismatch.' }
    $boot = Invoke-DedicatedAdb -Arguments @('shell', 'getprop', 'sys.boot_completed')
    $api = Invoke-DedicatedAdb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')
    $hardware = Invoke-DedicatedAdb -Arguments @('shell', 'getprop', 'ro.hardware')
    $fingerprint = Invoke-DedicatedAdb -Arguments @('shell', 'getprop', 'ro.build.fingerprint')
    $currentUser = Invoke-DedicatedAdb -Arguments @('shell', 'am', 'get-current-user')
    if ($boot -ne '1' -or $api -ne '35' -or $currentUser -ne '0' -or
        $hardware -notmatch '^(ranchu|goldfish|cutf_.*)$' -or
        $fingerprint -notmatch 'generic|sdk_gphone|sdk_phone|vsoc_') { throw 'Emulator boot/API/hardware/user guard failed.' }
    [pscustomobject]@{checkedAt=(Get-Date -Format o);avd=$avd;endpoint='127.0.0.1:5038';serial='emulator-5580';api=$api;qemuPid=$run.QemuProcessId} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportRoot 'emulator-identity.json')
}

Assert-DedicatedEmulator
if ($PreflightOnly) {
    Write-Host 'READ_ONLY_PREFLIGHT_OK: dedicated API 35 AVD verified; no build, wake, install or instrumentation.'
    return
}

# The shared SDK/JDK are provisioned once; this job's home, cache and temp are isolated.
New-Item -ItemType Directory -Force -Path $toolRoot | Out-Null
foreach ($part in @('jdk-25.0.5+8', 'sdk')) {
    $link = Join-Path $toolRoot $part
    $target = Join-Path 'D:\codex-tools\linzin-android' $part
    if (-not (Test-Path -LiteralPath $link)) { New-Item -ItemType Junction -Path $link -Target $target | Out-Null }
}
$pwsh = (Get-Command pwsh -ErrorAction Stop).Source
& $pwsh -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'build-android.ps1') -ToolRoot $toolRoot -LogPath (Join-Path $reportRoot 'build.log')
if ($LASTEXITCODE -ne 0) { throw 'Build/unit/lint/test-APK failed; instrumentation not started.' }
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($file in @($apk, $testApk)) { if (-not (Test-Path -LiteralPath $file)) { throw 'A freshly built APK is missing.' } }
Assert-DedicatedEmulator
$otherClients = @(Get-CimInstance Win32_Process -Filter "Name='adb.exe'" | Where-Object {
    $_.ProcessId -ne $run.AdbProcessId -and $_.CommandLine -match 'emulator-5580|\b5038\b'
})
if ($otherClients.Count -gt 0) { throw 'Another dedicated-emulator adb command is active; refusing to race.' }
$notificationsBefore = Invoke-DedicatedAdb -Arguments @('shell', 'settings', '--user', '0', 'get', 'secure', 'enabled_notification_listeners')
$notificationsBefore | Set-Content -LiteralPath (Join-Path $reportRoot 'notification-listeners-before.txt')

# Fixture mutations are confined to the already verified dedicated emulator.
Invoke-DedicatedAdb -Arguments @('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'wm', 'dismiss-keyguard') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'am', 'force-stop', '--user', '0', 'com.linzin.linzindeskclock') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'wm', 'size', '720x1280') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'wm', 'density', '320') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'settings', 'put', 'system', 'user_rotation', '1') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'wm', 'user-rotation', 'lock', '1') | Out-Null
Invoke-DedicatedAdb -Arguments @('shell', 'wm', 'fixed-to-user-rotation', 'enabled') | Out-Null
$googlePackage = Invoke-DedicatedAdb -Arguments @('shell', 'pm', 'path', 'com.google.android.googlequicksearchbox')
if ($googlePackage -match '^package:') {
    Invoke-DedicatedAdb -Arguments @('shell', 'pm', 'disable-user', '--user', '0', 'com.google.android.googlequicksearchbox') | Out-Null
    Invoke-DedicatedAdb -Arguments @('shell', 'am', 'force-stop', '--user', '0', 'com.google.android.googlequicksearchbox') | Out-Null
}
Invoke-DedicatedAdb -Arguments @('shell', 'dumpsys', 'display') | Set-Content -LiteralPath (Join-Path $reportRoot 'display-before.txt')
Invoke-DedicatedAdb -Arguments @('shell', 'dumpsys', 'media_session') | Set-Content -LiteralPath (Join-Path $reportRoot 'media-before.txt')
Get-FileHash -LiteralPath $apk, $testApk -Algorithm SHA256 | Select-Object Hash,Path | ConvertTo-Json |
    Set-Content -LiteralPath (Join-Path $reportRoot 'apk-hashes.json')
foreach ($file in @($apk, $testApk)) {
    $installed = Invoke-DedicatedAdb -Arguments @('install', '-r', '-t', '--user', '0', $file) -TimeoutSeconds 120
    if ($installed -notmatch '(?m)^Success\s*$') { throw 'APK install did not report Success.' }
}
Assert-DedicatedEmulator
try {
    $output = Invoke-DedicatedAdb -Arguments @('shell', 'am', 'instrument', '--user', '0', '-w', '-r', '-e', 'linzinDedicatedMediaEmulator', 'true', 'com.linzin.linzindeskclock.test/androidx.test.runner.AndroidJUnitRunner') -TimeoutSeconds 600
    $output | Set-Content -LiteralPath (Join-Path $reportRoot 'instrumentation.txt')
    # Custom media_artwork_capture events also use STATUS_CODE 0; only JUnit's
    # final summary supplies the test count. Any failure/skip or abnormal end fails.
    $summaries = [regex]::Matches($output, '(?m)^[ \t]*OK \((?<count>\d+) tests?\)[ \t]*\r?$')
    $passed = if ($summaries.Count -eq 1) { [int]$summaries[0].Groups['count'].Value } else { 0 }
    $failedOrSkipped = [regex]::Matches($output, '(?m)^INSTRUMENTATION_STATUS_CODE:[ \t]*-[1-4][ \t]*\r?$').Count
    $endCodes = [regex]::Matches($output, '(?m)^INSTRUMENTATION_CODE:[ \t]*(?<code>-?\d+)[ \t]*\r?$')
    $normalEnd = $endCodes.Count -eq 1 -and $endCodes[0].Groups['code'].Value -eq '-1'
    $success = $passed -eq 54 -and $failedOrSkipped -eq 0 -and $normalEnd -and
        $output -notmatch 'INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|FAILURES!!!'
    [pscustomobject]@{completedAt=(Get-Date -Format o);commit=$env:GITHUB_SHA;testsPassed=$passed;failedOrSkipped=$failedOrSkipped;expectedTests=54;success=$success} |
        ConvertTo-Json | Tee-Object -FilePath (Join-Path $reportRoot 'result.json')
    if (-not $success) { throw 'Instrumentation must execute and pass all 54 tests without skips.' }
} finally {
    $after = Invoke-DedicatedAdb -Arguments @('shell', 'settings', '--user', '0', 'get', 'secure', 'enabled_notification_listeners')
    $after | Set-Content -LiteralPath (Join-Path $reportRoot 'notification-listeners-after.txt')
    if ($after -cne $notificationsBefore) { throw 'Listener grants changed; inspect the dedicated AVD before reuse. No global restoration attempted.' }
}
Write-Host 'SELF_HOSTED_VALIDATION_PASSED: debug APK, JVM tests, lint, test APK and 54 dedicated-emulator tests.'
