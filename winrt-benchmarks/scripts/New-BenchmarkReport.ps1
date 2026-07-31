[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ResultsDirectory,

    [Parameter(Mandatory = $true)]
    [string]$MarkdownOutput,

    [Parameter(Mandatory = $true)]
    [string]$JsonOutput
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$runnerFiles = [ordered]@{
    "cppwinrt" = "cppwinrt.jsonl"
    "cswinrt" = "cswinrt.jsonl"
    "kotlin-native" = "kotlin-native.jsonl"
    "kotlin-jvm" = "kotlin-jvm.jsonl"
}

$recordsByRunner = [ordered]@{}
foreach ($entry in $runnerFiles.GetEnumerator()) {
    $path = Join-Path $ResultsDirectory $entry.Value
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Missing benchmark result for '$($entry.Key)': $path"
    }

    $records = @(
        Get-Content -LiteralPath $path |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object { $_ | ConvertFrom-Json }
    )
    if ($records.Count -eq 0) {
        throw "Benchmark result '$path' contains no records."
    }
    foreach ($record in $records) {
        if ([int]$record.schemaVersion -ne 1) {
            throw "Unsupported benchmark schema version '$($record.schemaVersion)' in '$path'."
        }
        if ([string]$record.runner -ne $entry.Key) {
            throw "Runner '$($record.runner)' in '$path' does not match expected runner '$($entry.Key)'."
        }
    }
    $recordsByRunner[$entry.Key] = $records
}

$referenceRecords = @($recordsByRunner["cppwinrt"])
$scenarioOrder = @($referenceRecords | ForEach-Object { [string]$_.scenario })
$uniqueScenarios = @($scenarioOrder | Select-Object -Unique)
if ($uniqueScenarios.Count -ne $scenarioOrder.Count) {
    throw "C++/WinRT result contains duplicate scenario records."
}

$referenceConfiguration = $referenceRecords[0]
$recordsByScenario = [ordered]@{}
foreach ($runner in $runnerFiles.Keys) {
    $runnerRecords = @($recordsByRunner[$runner])
    $runnerScenarios = @($runnerRecords | ForEach-Object { [string]$_.scenario })
    $difference = @(Compare-Object -ReferenceObject $scenarioOrder -DifferenceObject $runnerScenarios)
    if ($difference.Count -ne 0) {
        throw "Runner '$runner' did not execute the same scenario set as C++/WinRT."
    }

    $scenarioMap = [ordered]@{}
    foreach ($record in $runnerRecords) {
        if ([int]$record.warmupRounds -ne [int]$referenceConfiguration.warmupRounds -or
            [int]$record.measurementRounds -ne [int]$referenceConfiguration.measurementRounds -or
            [int]$record.iterations -ne [int]$referenceConfiguration.iterations) {
            throw "Runner '$runner' used benchmark parameters that differ from C++/WinRT."
        }
        $scenarioMap[[string]$record.scenario] = $record
    }
    $recordsByScenario[$runner] = $scenarioMap
}

function Format-Number([double]$Value) {
    return $Value.ToString("0.00", [System.Globalization.CultureInfo]::InvariantCulture)
}

function Format-Ratio([double]$Value) {
    return $Value.ToString("0.000", [System.Globalization.CultureInfo]::InvariantCulture) + "x"
}

$rows = @()
foreach ($scenario in $scenarioOrder) {
    $cpp = $recordsByScenario["cppwinrt"][$scenario]
    $cs = $recordsByScenario["cswinrt"][$scenario]
    $kotlinNative = $recordsByScenario["kotlin-native"][$scenario]
    $kotlinJvm = $recordsByScenario["kotlin-jvm"][$scenario]

    $checksums = @(
        @(
            [long]$cpp.checksum,
            [long]$cs.checksum,
            [long]$kotlinNative.checksum,
            [long]$kotlinJvm.checksum
        ) | Select-Object -Unique
    )
    if ($checksums.Count -ne 1) {
        throw "Scenario '$scenario' produced different checksums across projections."
    }

    $cppMedian = [double]$cpp.medianNsPerOp
    $csMedian = [double]$cs.medianNsPerOp
    $nativeMedian = [double]$kotlinNative.medianNsPerOp
    $jvmMedian = [double]$kotlinJvm.medianNsPerOp
    $rows += [ordered]@{
        scenario = $scenario
        cppWinRTNsPerOp = $cppMedian
        csWinRTNsPerOp = $csMedian
        kotlinNativeNsPerOp = $nativeMedian
        kotlinJvmNsPerOp = $jvmMedian
        kotlinNativeVsCppWinRT = $nativeMedian / $cppMedian
        kotlinJvmVsCsWinRT = $jvmMedian / $csMedian
        csWinRTVsCppWinRT = $csMedian / $cppMedian
        checksum = [long]$cpp.checksum
    }
}

$runtimes = [ordered]@{}
foreach ($runner in $runnerFiles.Keys) {
    $runtimes[$runner] = [string]$recordsByRunner[$runner][0].runtime
}

$report = [ordered]@{
    schemaVersion = 1
    generatedAtUtc = [DateTime]::UtcNow.ToString("O", [System.Globalization.CultureInfo]::InvariantCulture)
    configuration = [ordered]@{
        warmupRounds = [int]$referenceConfiguration.warmupRounds
        measurementRounds = [int]$referenceConfiguration.measurementRounds
        iterations = [int]$referenceConfiguration.iterations
    }
    runtimes = $runtimes
    results = $rows
}

$markdown = @(
    "# WinRT Projection Benchmark",
    "",
    "All runners execute the same Windows.Data.Json operations in x64 Release mode. Values are median nanoseconds per operation; lower is better.",
    "",
    "- Warmup rounds: $($referenceConfiguration.warmupRounds)",
    "- Measurement rounds: $($referenceConfiguration.measurementRounds)",
    "- Iterations per round: $($referenceConfiguration.iterations)",
    "",
    "| Scenario | C++/WinRT ns/op | CsWinRT ns/op | Kotlin/Native ns/op | Kotlin/JVM ns/op | K/N vs C++ | K/JVM vs Cs |",
    "| --- | ---: | ---: | ---: | ---: | ---: | ---: |"
)
foreach ($row in $rows) {
    $markdown += "| $($row.scenario) | $(Format-Number $row.cppWinRTNsPerOp) | $(Format-Number $row.csWinRTNsPerOp) | $(Format-Number $row.kotlinNativeNsPerOp) | $(Format-Number $row.kotlinJvmNsPerOp) | $(Format-Ratio $row.kotlinNativeVsCppWinRT) | $(Format-Ratio $row.kotlinJvmVsCsWinRT) |"
}
$markdown += @(
    "",
    "## Runtimes",
    ""
)
foreach ($runner in $runnerFiles.Keys) {
    $markdown += "- ``$runner``: $($runtimes[$runner])"
}
$markdownText = $markdown -join [Environment]::NewLine

foreach ($output in @($MarkdownOutput, $JsonOutput)) {
    $parent = Split-Path -Parent $output
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
}
Set-Content -LiteralPath $MarkdownOutput -Value $markdownText -Encoding utf8
$report | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $JsonOutput -Encoding utf8

Write-Host "Benchmark report: $MarkdownOutput"
