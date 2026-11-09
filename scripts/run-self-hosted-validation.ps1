[CmdletBinding()]
param(
    [switch]$PreflightOnly,
    [string]$ExpectedBranch = $env:LINZIN_EXPECTED_BRANCH,
    [string]$ExpectedSha = $env:LINZIN_EXPECTED_SHA,
    [switch]$CollectBurnInScreenshots
)

$ErrorActionPreference = 'Stop'

# Pure policy function: parser tests load this definition without running any host/device work.
function Assert-SelfHostedContext {
    param([hashtable]$Context, [string]$ExpectedBranch, [string]$ExpectedSha,
        [string]$HeadSha, [switch]$PreflightOnly)
    foreach ($name in @('LINZIN_SELF_HOSTED_ENABLED', 'LINZIN_SELF_HOSTED_REPOSITORY',
        'LINZIN_SELF_HOSTED_ACTOR', 'LINZIN_SELF_HOSTED_BRANCH')) {
        if ([string]::IsNullOrWhiteSpace($Context[$name])) { throw "Missing self-hosted configuration: $name" }
    }
    if ($Context.LINZIN_SELF_HOSTED_ENABLED -cne 'true') { throw 'Self-hosted validation requires explicit opt-in.' }
    if ([string]::IsNullOrWhiteSpace($ExpectedBranch) -or
        $ExpectedBranch -cne $Context.LINZIN_SELF_HOSTED_BRANCH) {
        throw 'Expected branch must equal the configured reviewed branch.'
    }
    # This mode only checks the dedicated emulator; it cannot build or install.
    if ($PreflightOnly) { return }
    if ($Context.GITHUB_EVENT_NAME -cne 'workflow_dispatch' -or
        $Context.GITHUB_REPOSITORY -cne $Context.LINZIN_SELF_HOSTED_REPOSITORY -or
        $Context.GITHUB_ACTOR -cne $Context.LINZIN_SELF_HOSTED_ACTOR -or
        $Context.GITHUB_TRIGGERING_ACTOR -cne $Context.LINZIN_SELF_HOSTED_ACTOR -or
        $Context.GITHUB_REF -cne "refs/heads/$ExpectedBranch") {
        throw 'Repository, branch, actor, rerun actor or manual event guard failed.'
    }
    if ($ExpectedSha -cnotmatch '^[0-9a-f]{40}$' -or
        $Context.GITHUB_SHA -cne $ExpectedSha -or $HeadSha -cne $ExpectedSha) {
        throw 'Requested SHA, triggering SHA and checkout HEAD must match exactly.'
    }
}

# Reviewed suite counts: update explicitly when tests are added; never infer from run output.
$expectedAndroidTests = 92
$expectedJvmTests = 174
$qaRoot = 'D:\codex-tools\linzin-android\runner-QA'
$toolRoot = Join-Path $qaRoot 'toolchain'
$projectRoot = Split-Path -Parent $PSScriptRoot
$adb = 'D:\codex-tools\linzin-android\sdk\platform-tools\adb.exe'
$reportRoot = if ($PreflightOnly) { Join-Path $qaRoot 'evidence\preflight' } else { Join-Path $projectRoot '.local\self-hosted-validation' }
$context = @{}
foreach ($name in @('LINZIN_SELF_HOSTED_ENABLED', 'LINZIN_SELF_HOSTED_REPOSITORY',
    'LINZIN_SELF_HOSTED_ACTOR', 'LINZIN_SELF_HOSTED_BRANCH', 'GITHUB_EVENT_NAME',
    'GITHUB_REPOSITORY', 'GITHUB_ACTOR', 'GITHUB_TRIGGERING_ACTOR', 'GITHUB_REF', 'GITHUB_SHA')) {
    $context[$name] = [Environment]::GetEnvironmentVariable($name)
}
$head = ''
if (-not $PreflightOnly) {
    $head = (& git -C $projectRoot rev-parse HEAD)
    if ($LASTEXITCODE -ne 0) { throw 'Cannot resolve checkout HEAD.' }
    $head = $head.Trim()
}
Assert-SelfHostedContext -Context $context -ExpectedBranch $ExpectedBranch -ExpectedSha $ExpectedSha -HeadSha $head -PreflightOnly:$PreflightOnly
& git check-ref-format "refs/heads/$ExpectedBranch"
if ($LASTEXITCODE -ne 0) { throw 'Expected branch is not a valid Git branch name.' }
if (-not $PreflightOnly) {
    $allowedWork = [IO.Path]::GetFullPath((Join-Path $qaRoot 'work')) + '\'
    if (-not [IO.Path]::GetFullPath($projectRoot).StartsWith($allowedWork, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Use the isolated QA runner checkout.'
    }
}
New-Item -ItemType Directory -Force -Path $reportRoot | Out-Null
$run = Get-Content -LiteralPath 'D:\codex-tools\linzin-android\emulator-qa\current-run.json' -Raw | ConvertFrom-Json
if ($run.AvdName -cne 'Linzin_API35_GoogleApis_QA' -or $run.Serial -cne 'emulator-5580' -or
    $run.AdbHost -cne '127.0.0.1' -or $run.AdbServerPort -ne 5038 -or
    $run.QemuProcessId -le 0 -or -not $run.QemuStartTime) { throw 'Dedicated emulator record mismatch; verified QEMU PID/start time required.' }

function Get-InstrumentationResult {
    param([string]$Output, [Parameter(Mandatory)][ValidateRange(1, 10000)][int]$ExpectedTests)
    # Custom capture events also use STATUS_CODE 0; count only the final JUnit summary.
    $summaries = [regex]::Matches($Output, '(?m)^[ \t]*OK \((?<count>[0-9]+) tests?\)[ \t]*\r?$')
    $passed = 0
    if ($summaries.Count -eq 1) { [void][int]::TryParse($summaries[0].Groups['count'].Value, [ref]$passed) }
    $statusLines = [regex]::Matches($Output, '(?m)^[ \t]*INSTRUMENTATION_STATUS_CODE:[^\r\n]*')
    $failedOrSkipped = @($statusLines | Where-Object {
        $_.Value -cnotmatch '^\s*INSTRUMENTATION_STATUS_CODE:[ \t]*[012][ \t]*$'
    }).Count
    $endCodes = [regex]::Matches($Output, '(?m)^[ \t]*INSTRUMENTATION_CODE:[^\r\n]*')
    $end = [regex]::Match($Output, '(?m)^[ \t]*INSTRUMENTATION_CODE:[ \t]*-1[ \t\r\n]*\z')
    $normalEnd = $endCodes.Count -eq 1 -and $end.Success -and
        $summaries.Count -eq 1 -and $summaries[0].Index -lt $end.Index
    $reportedError = $Output -match 'INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|FAILURES!!!' -or
        $Output -match '(?mi)^[ \t]*INSTRUMENTATION_(?:STATUS|RESULT):[ \t]*(?:Error|shortMsg|longMsg|stack)[ \t]*='
    [pscustomobject]@{
        testsPassed = $passed; expectedTests = $ExpectedTests; failedOrSkipped = $failedOrSkipped
        normalEnd = $normalEnd; reportedError = $reportedError
        success = $passed -eq $ExpectedTests -and $failedOrSkipped -eq 0 -and $normalEnd -and -not $reportedError
    }
}

function Get-JvmTestResult {
    param([AllowEmptyCollection()][string[]]$Reports, [Parameter(Mandatory)][ValidateRange(1, 10000)][int]$ExpectedTests)
    $total = 0L
    $valid = @($Reports).Count -gt 0
    foreach ($report in $Reports) {
        try {
            $document = [xml]::new()
            $document.XmlResolver = $null
            $document.LoadXml($report)
            if ($document.DocumentElement.LocalName -cne 'testsuite' -or $document.DocumentType) { throw 'Invalid JUnit report root.' }
            $counts = @{}
            foreach ($name in @('tests', 'failures', 'errors', 'skipped')) {
                $value = $document.DocumentElement.GetAttribute($name)
                $number = 0
                if ($value -cnotmatch '^[0-9]+$' -or -not [int]::TryParse($value, [ref]$number)) { throw 'Invalid JUnit counter.' }
                $counts[$name] = $number
            }
            $cases = $document.SelectNodes('/testsuite/testcase').Count
            if ($cases -ne $counts.tests -or $counts.failures -ne 0 -or $counts.errors -ne 0 -or $counts.skipped -ne 0 -or
                $document.SelectNodes('//failure | //error | //skipped').Count -ne 0) { $valid = $false }
            $total += $cases
        } catch { $valid = $false }
    }
    [pscustomobject]@{testsPassed=$total;expectedTests=$ExpectedTests;success=$valid -and $total -eq $ExpectedTests}
}

function Assert-PrivateAdbServer {
    $server = Get-Process -Id $run.AdbProcessId -ErrorAction Stop
    if ($server.Path -ne $adb -or $server.StartTime -ne [datetime]$run.AdbStartTime) {
        throw 'Recorded private adb process exited or was replaced; refusing to start another server.'
    }
    $serverInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($run.AdbProcessId)"
    if ($serverInfo.CommandLine -notmatch '--one-device\s+emulator-5580(?:\s|$)' -or
        $serverInfo.CommandLine -notmatch '-L\s+tcp:5038(?:\s|$)') { throw 'Private adb server isolation mismatch.' }
    $qemu = Get-Process -Id $run.QemuProcessId -ErrorAction Stop
    if ($qemu.ProcessName -notlike 'qemu-system-x86_64*' -or $qemu.StartTime -ne [datetime]$run.QemuStartTime) {
        throw 'Recorded QEMU process exited or was replaced.'
    }
    $qemuInfo = Get-CimInstance Win32_Process -Filter "ProcessId=$($run.QemuProcessId)"
    if ($qemuInfo.CommandLine -notmatch '(?:^|\s)-avd\s+"?Linzin_API35_GoogleApis_QA"?(?:\s|$)' -or
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
            $stderr.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $reportRoot 'timed-out-adb-stderr.txt')
            throw 'Dedicated adb timed out. Device-side work may still run; inspect before retrying.'
        }
        $text = $stdout.GetAwaiter().GetResult()
        $errorText = $stderr.GetAwaiter().GetResult()
        if ($process.ExitCode -ne 0) {
            $text | Set-Content -LiteralPath (Join-Path $reportRoot 'failed-adb-stdout.txt')
            $errorText | Set-Content -LiteralPath (Join-Path $reportRoot 'failed-adb-stderr.txt')
            throw "Dedicated adb failed ($($process.ExitCode)): $errorText"
        }
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
    [pscustomobject]@{checkedAt=(Get-Date -Format o);avd=$avd;endpoint='127.0.0.1:5038';serial='emulator-5580';api=$api;qemuPid=$run.QemuProcessId;qemuStartTime=$run.QemuStartTime} |
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
$buildStarted = [datetime]::UtcNow
& $pwsh -NoProfile -NonInteractive -File (Join-Path $PSScriptRoot 'build-android.ps1') -ToolRoot $toolRoot -LogPath (Join-Path $reportRoot 'build.log') -CiValidation
if ($LASTEXITCODE -ne 0) { throw 'Build/unit/lint/test-APK failed; instrumentation not started.' }
$jvmReports = @(Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app\build\test-results\testDebugUnitTest') -Filter 'TEST-*.xml' -File)
if (@($jvmReports | Where-Object { $_.LastWriteTimeUtc -lt $buildStarted }).Count) { throw 'Stale JVM test reports.' }
$jvmResult = Get-JvmTestResult -Reports @($jvmReports | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -ExpectedTests $expectedJvmTests
$jvmResult | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $reportRoot 'jvm-result.json')
if (-not $jvmResult.success) { throw "JVM reports must contain all $expectedJvmTests tests without failures, errors or skips." }
$apk = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$testApk = Join-Path $projectRoot 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk'
foreach ($file in @($apk, $testApk)) { if (-not (Test-Path -LiteralPath $file)) { throw 'A freshly built APK is missing.' } }
Assert-DedicatedEmulator
$otherClients = @(Get-CimInstance Win32_Process -Filter "Name='adb.exe'" | Where-Object {
    $_.ProcessId -ne $run.AdbProcessId -and $_.CommandLine -match 'emulator-5580|\b5038\b'
})
if ($otherClients.Count -gt 0) { throw 'Another dedicated-emulator adb command is active; refusing to race.' }
# This disposable AVD may contain a local build signed with a different debug key.
# Start from a clean installation of these two fixed test packages only. Never run this on USB.
foreach ($package in @('com.linzin.linzindeskclock.test', 'com.linzin.linzindeskclock')) {
    $existing = Invoke-DedicatedAdb -Arguments @('shell', 'pm', 'list', 'packages', '--user', '0', $package)
    if (($existing -split "\r?\n") -contains "package:$package") {
        $removed = Invoke-DedicatedAdb -Arguments @('uninstall', $package) -TimeoutSeconds 60
        if ($removed -notmatch '(?m)^Success\s*$') { throw "Could not remove dedicated emulator fixture $package." }
    }
}
$notificationsBefore = Invoke-DedicatedAdb -Arguments @('shell', 'settings', '--user', '0', 'get', 'secure', 'enabled_notification_listeners')
$notificationsBefore | Set-Content -LiteralPath (Join-Path $reportRoot 'notification-listeners-before.txt')
$burnInRoot = '/sdcard/Android/data/com.linzin.linzindeskclock/cache/burn-in-verification'
$burnInNames = @('before-idle', 'idle-phase-0', 'progress-update-still-protected', 'idle-phase-1',
    'after-touch', 'idle-after-touch', 'disabled-before', 'disabled-after-idle')

$instrumentationResult = $null
$validationPassed = $false
$cleanupErrors = @()
$validationError = $null
# Fixture mutations are confined to the already verified dedicated emulator.
# Keep the entire fixture inside finally so failed setup/install also resets battery state.
try {
    Invoke-DedicatedAdb -Arguments @('shell', 'dumpsys', 'battery', 'reset') | Out-Null
    Invoke-DedicatedAdb -Arguments @('shell', 'locksettings', 'set-disabled', '--user', '0', 'true') | Out-Null
    $lockDisabled = Invoke-DedicatedAdb -Arguments @('shell', 'locksettings', 'get-disabled', '--user', '0')
    if ($lockDisabled -cne 'true') { throw 'Dedicated emulator lockscreen is not disabled.' }
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
    if ($CollectBurnInScreenshots) {
        # Clear only the eight fixed fixture names; never pull paths reported by test output.
        foreach ($name in $burnInNames) {
            Invoke-DedicatedAdb -Arguments @('shell', 'rm', '-f', "$burnInRoot/$name.png") | Out-Null
        }
    }
    $output = Invoke-DedicatedAdb -Arguments @('shell', 'am', 'instrument', '--user', '0', '-w', '-r', '-e', 'linzinDedicatedMediaEmulator', 'true', 'com.linzin.linzindeskclock.test/androidx.test.runner.AndroidJUnitRunner') -TimeoutSeconds 1200
    $output | Set-Content -LiteralPath (Join-Path $reportRoot 'instrumentation.txt')
    $instrumentationResult = Get-InstrumentationResult -Output $output -ExpectedTests $expectedAndroidTests
    if (-not $instrumentationResult.success) { throw "Instrumentation must execute and pass all $expectedAndroidTests tests without skips or abnormal output." }
    $validationPassed = $true
} catch {
    $validationError = $_
} finally {
    try {
        Invoke-DedicatedAdb -Arguments @('shell', 'dumpsys', 'battery', 'reset') | Out-Null
    } catch { $cleanupErrors += "Battery reset failed: $_" }
    try {
        $after = Invoke-DedicatedAdb -Arguments @('shell', 'settings', '--user', '0', 'get', 'secure', 'enabled_notification_listeners')
        $after | Set-Content -LiteralPath (Join-Path $reportRoot 'notification-listeners-after.txt')
        if ($after -cne $notificationsBefore) { throw 'Listener grants changed; inspect the dedicated AVD before reuse. No global restoration attempted.' }
    } catch { $cleanupErrors += "Listener restoration verification failed: $_" }
    if ($CollectBurnInScreenshots) {
        $captureRoot = Join-Path $reportRoot 'burn-in-verification'
        New-Item -ItemType Directory -Force -Path $captureRoot | Out-Null
        foreach ($name in $burnInNames) {
            try {
                Invoke-DedicatedAdb -Arguments @('pull', "$burnInRoot/$name.png", (Join-Path $captureRoot "$name.png")) -TimeoutSeconds 10 | Out-Null
            } catch { Write-Warning "Optional burn-in capture $name unavailable: $_" }
        }
    }
    $success = $validationPassed -and $cleanupErrors.Count -eq 0
    [pscustomobject]@{
        completedAt=(Get-Date -Format o);commit=$env:GITHUB_SHA;expectedTests=$expectedAndroidTests
        testsPassed=if ($instrumentationResult) { $instrumentationResult.testsPassed } else { 0 }
        failedOrSkipped=if ($instrumentationResult) { $instrumentationResult.failedOrSkipped } else { $null }
        jvmTestsPassed=$jvmResult.testsPassed;expectedJvmTests=$expectedJvmTests
        success=$success;error=if ($validationError) { [string]$validationError } else { $null };cleanupErrors=$cleanupErrors
    } | ConvertTo-Json | Tee-Object -FilePath (Join-Path $reportRoot 'result.json')
}
if ($validationError) { throw $validationError }
if ($cleanupErrors.Count) { throw ($cleanupErrors -join ' ; ') }
Write-Host "SELF_HOSTED_VALIDATION_PASSED: debug APK, $expectedJvmTests JVM tests, lint, test APK and $expectedAndroidTests dedicated-emulator tests."
