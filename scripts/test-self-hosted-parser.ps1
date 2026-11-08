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
foreach ($name in @('Get-InstrumentationResult', 'Get-JvmTestResult')) {
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
Write-Host "PARSER_VALIDATION_PASSED: $script:checks assertions; no runner, build, network or device operations."
