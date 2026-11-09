[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$script:checks = 0
$validationAst = $null
foreach ($name in @('run-self-hosted-validation.ps1', 'build-android.ps1', 'test-self-hosted-parser.ps1')) {
    $tokens = $null; $parseErrors = $null
    $ast = [Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot $name), [ref]$tokens, [ref]$parseErrors)
    if ($parseErrors.Count) { throw ($parseErrors | Out-String) }
    if ($name -eq 'run-self-hosted-validation.ps1') { $validationAst = $ast }
    $script:checks++
}
# Load only pure parser definitions: never dot-source the runner or execute its ADB/build.
foreach ($name in @('Get-InstrumentationResult', 'Get-JvmTestResult', 'Assert-SelfHostedContext')) {
    $definition = $validationAst.FindAll({ param($node)
        $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq $name
    }, $false)
    if ($definition.Count -ne 1) { throw "Expected exactly one pure parser: $name" }
    . ([scriptblock]::Create($definition[0].Extent.Text))
}

function Assert-Equal($Actual, $Expected, [string]$Name) {
    if ($Actual -cne $Expected) { throw "$Name expected '$Expected', got '$Actual'." }
    $script:checks++
}

# Custom capture events add STATUS_CODE 0 without adding JUnit test completions.
$good = @'
INSTRUMENTATION_STATUS: class=example.ClockTest
INSTRUMENTATION_STATUS: test=clock
INSTRUMENTATION_STATUS_CODE: 1
INSTRUMENTATION_STATUS: burn_in_capture=/storage/emulated/0/Android/data/com.linzin.linzindeskclock/cache/burn-in-verification/before-idle.png
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_STATUS_CODE: 2
INSTRUMENTATION_STATUS_CODE: 0
INSTRUMENTATION_RESULT: stream=
Time: 504.123

OK (82 tests)

INSTRUMENTATION_CODE: -1
'@
$good = $good.Replace("`r`n", "`n")
foreach ($text in @($good, ($good + "`n "), $good.Replace("`n", "`r`n"))) {
    $result = Get-InstrumentationResult -ExpectedTests 82 -Output $text
    Assert-Equal $result.success $true '82 tests including capture events'
    Assert-Equal $result.testsPassed 82 'JUnit summary is authoritative'
    Assert-Equal $result.expectedTests 82 'Fixed Android expectation'
}
Assert-Equal (Get-InstrumentationResult -ExpectedTests 83 -Output $good.Replace('82 tests', '83 tests')).success $true 'Explicit updated Android count'
Assert-Equal (Get-InstrumentationResult -ExpectedTests 83 -Output $good).success $false 'Updated suite rejects obsolete count'
Assert-Equal (Get-InstrumentationResult -ExpectedTests 84 -Output $good.Replace('82 tests', '84 tests')).success $true 'Future explicit count requires no parser relaxation'
$badOutputs = [ordered]@{
    'old suite' = $good.Replace('82 tests', '54 tests')
    'partial suite' = $good.Replace('82 tests', '81 tests')
    'extra test' = $good.Replace('82 tests', '83 tests')
    'zero tests' = $good.Replace('82 tests', '0 tests')
    'overflow count' = $good.Replace('82 tests', '999999999999999999999999 tests')
    'missing summary' = $good.Replace('OK (82 tests)', '')
    'duplicate summary' = $good.Replace('OK (82 tests)', "OK (82 tests)`nOK (82 tests)")
    'missing end' = $good.Replace('INSTRUMENTATION_CODE: -1', '')
    'abnormal end' = $good.Replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: 0')
    'malformed end' = $good.Replace('INSTRUMENTATION_CODE: -1', 'INSTRUMENTATION_CODE: invalid')
    'duplicate end' = $good + "`nINSTRUMENTATION_CODE: -1"
    'early end' = "INSTRUMENTATION_CODE: -1`n" + $good.Replace('INSTRUMENTATION_CODE: -1', '')
    'trailing output' = $good + "`nProcess crashed."
    'failed marker' = "INSTRUMENTATION_FAILED: runner failed`n" + $good
    'aborted marker' = "INSTRUMENTATION_ABORTED`n" + $good
    'JUnit failure' = "FAILURES!!!`n" + $good
    'status error' = "INSTRUMENTATION_STATUS: Error=runner failed`n" + $good
    'result shortMsg' = "INSTRUMENTATION_RESULT: shortMsg=Process crashed.`n" + $good
    'result longMsg' = "INSTRUMENTATION_RESULT: longMsg=runner failed`n" + $good
    'status stack' = "INSTRUMENTATION_STATUS: stack=java.lang.AssertionError`n" + $good
    'empty error' = "INSTRUMENTATION_RESULT: shortMsg=`n" + $good
}
foreach ($code in @('-1', '-2', '-3', '-4', '-5', '3', 'invalid', '')) {
    $badOutputs["rejected status '$code'"] = "INSTRUMENTATION_STATUS_CODE: $code`n" + $good
}
foreach ($case in $badOutputs.GetEnumerator()) {
    Assert-Equal (Get-InstrumentationResult -ExpectedTests 82 -Output $case.Value).success $false $case.Key
}
Assert-Equal (Get-InstrumentationResult -ExpectedTests 82 -Output "INSTRUMENTATION_STATUS_CODE: -3`n$good").failedOrSkipped 1 'Ignored test diagnostic'

function New-Report([int]$Count, [string]$Counters = 'failures="0" errors="0" skipped="0"', [string]$FirstChild = '') {
    $cases = for ($i = 0; $i -lt $Count; $i++) {
        $child = if ($i -eq 0) { $FirstChild } else { '' }
        '<testcase name="test{0}">{1}</testcase>' -f $i, $child
    }
    '<testsuite name="example.ClockTest" tests="{0}" {1}>{2}</testsuite>' -f $Count, $Counters, ($cases -join '')
}
$goodXml = New-Report 148
$result = Get-JvmTestResult -ExpectedTests 148 -Reports @((New-Report 100), (New-Report 48))
Assert-Equal $result.success $true '148 JVM tests across reports'
Assert-Equal $result.testsPassed 148 'JVM actual test count'
Assert-Equal $result.expectedTests 148 'Fixed JVM expectation'
Assert-Equal (Get-JvmTestResult -ExpectedTests 148 -Reports @($goodXml)).success $true 'Single full JVM report'
Assert-Equal (Get-JvmTestResult -ExpectedTests 152 -Reports @((New-Report 152))).success $true 'Explicit updated JVM count'
$badReports = [ordered]@{
    'missing JVM reports' = @()
    'partial JVM suite' = @((New-Report 147))
    'extra JVM test' = @((New-Report 149))
    'skipped JVM test' = @((New-Report 148 'failures="0" errors="0" skipped="1"' '<skipped/>'))
    'failing JVM test' = @((New-Report 148 'failures="1" errors="0" skipped="0"' '<failure/>'))
    'JVM error' = @((New-Report 148 'failures="0" errors="1" skipped="0"' '<error/>'))
    'missing counters' = @((New-Report 148 ''))
    'invalid counters' = @((New-Report 148 'failures="NaN" errors="0" skipped="0"'))
    'negative counters' = @((New-Report 148 'failures="-1" errors="0" skipped="0"'))
    'declared count disagrees with cases' = @($goodXml.Replace('tests="148"', 'tests="149"'))
    'hidden skipped node' = @((New-Report 148 'failures="0" errors="0" skipped="0"' '<skipped/>'))
    'hidden failure node' = @((New-Report 148 'failures="0" errors="0" skipped="0"' '<failure/>'))
    'malformed XML' = @('<testsuite')
    'wrong root' = @('<not-testsuite/>')
}
foreach ($case in $badReports.GetEnumerator()) {
    Assert-Equal (Get-JvmTestResult -ExpectedTests 148 -Reports $case.Value).success $false $case.Key
}

# Synthetic contexts exercise authorisation without loading the runner entrypoint.
function Assert-ContextRejected([hashtable]$Context, [string]$Name,
    [string]$Branch = 'codex/desk-clock-v2-upstream', [string]$Sha = ('a' * 40),
    [string]$Head = ('a' * 40), [switch]$Preflight) {
    $rejected = $false
    try { Assert-SelfHostedContext -Context $Context -ExpectedBranch $Branch -ExpectedSha $Sha -HeadSha $Head -PreflightOnly:$Preflight }
    catch { $rejected = $true }
    Assert-Equal $rejected $true $Name
}
$context = @{
    LINZIN_SELF_HOSTED_ENABLED = 'true'
    LINZIN_SELF_HOSTED_REPOSITORY = 'example-org/example-repo'
    LINZIN_SELF_HOSTED_ACTOR = 'example-maintainer'
    LINZIN_SELF_HOSTED_BRANCH = 'codex/desk-clock-v2-upstream'
    GITHUB_REPOSITORY = 'example-org/example-repo'
    GITHUB_ACTOR = 'example-maintainer'
    GITHUB_TRIGGERING_ACTOR = 'example-maintainer'
    GITHUB_EVENT_NAME = 'workflow_dispatch'
    GITHUB_REF = 'refs/heads/codex/desk-clock-v2-upstream'
    GITHUB_SHA = ('a' * 40)
}
Assert-SelfHostedContext -Context $context -ExpectedBranch $context.LINZIN_SELF_HOSTED_BRANCH -ExpectedSha ('a' * 40) -HeadSha ('a' * 40)
$script:checks++
foreach ($key in $context.Keys) {
    $missing = $context.Clone(); $missing.Remove($key)
    Assert-ContextRejected $missing "Missing $key fails closed"
    $empty = $context.Clone(); $empty[$key] = ''
    Assert-ContextRejected $empty "Empty $key fails closed"
}
$changes = @{
    LINZIN_SELF_HOSTED_ENABLED = @('false', 'TRUE', '1', ' ')
    LINZIN_SELF_HOSTED_REPOSITORY = @('other-org/example-repo', ' ')
    LINZIN_SELF_HOSTED_ACTOR = @('another-maintainer', ' ')
    LINZIN_SELF_HOSTED_BRANCH = @('main', ' ')
    GITHUB_REPOSITORY = @('other-org/example-repo')
    GITHUB_ACTOR = @('another-maintainer')
    GITHUB_TRIGGERING_ACTOR = @('another-maintainer')
    GITHUB_EVENT_NAME = @('pull_request', 'pull_request_target', 'push', 'workflow_run', 'schedule', 'workflow_call')
    GITHUB_REF = @('refs/heads/main', 'refs/tags/codex/desk-clock-v2-upstream', 'refs/pull/1/merge')
    GITHUB_SHA = @(('b' * 40))
}
foreach ($key in $changes.Keys) {
    foreach ($value in $changes[$key]) {
        $changed = $context.Clone(); $changed[$key] = $value
        Assert-ContextRejected $changed "Rejected $key=$value"
    }
}
foreach ($branch in @('', 'main', ' ', 'refs/heads/codex/desk-clock-v2-upstream')) {
    Assert-ContextRejected $context 'Explicit matching branch required' -Branch $branch
}
foreach ($sha in @('', 'main', ('a' * 39), ('a' * 41), ('A' * 40), ('b' * 40))) {
    Assert-ContextRejected $context 'Full matching lowercase SHA required' -Sha $sha
}
Assert-ContextRejected $context 'Checkout must equal the requested and triggering SHA' -Head ('b' * 40)
# Preflight is read-only but still requires explicit configuration and branch.
$preflight = $context.Clone()
foreach ($key in @($preflight.Keys)) { if ($key.StartsWith('GITHUB_')) { $preflight.Remove($key) } }
Assert-SelfHostedContext -Context $preflight -ExpectedBranch $context.LINZIN_SELF_HOSTED_BRANCH -PreflightOnly
$script:checks++
foreach ($key in @($preflight.Keys)) {
    $missing = $preflight.Clone(); $missing.Remove($key)
    Assert-ContextRejected $missing "Preflight requires $key" -Preflight
}

Write-Host "PARSER_VALIDATION_PASSED: $script:checks assertions; no runner, build, network or device operations."
