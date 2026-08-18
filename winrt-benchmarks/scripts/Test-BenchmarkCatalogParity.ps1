[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ReferenceDirectory,

    [Parameter(Mandatory = $true)]
    [string]$KotlinJvmCatalog,

    [Parameter(Mandatory = $true)]
    [string]$KotlinNativeCatalog,

    [Parameter(Mandatory = $true)]
    [string]$CsWinRTCatalog,

    [Parameter(Mandatory = $true)]
    [string]$CppWinRTCatalog
)

$ErrorActionPreference = "Stop"

$benchmarkRoot = Split-Path -Parent $PSScriptRoot
$forbiddenWorkloadPattern = 'Windows\.Data\.Json|windows\.`data`\.json|\bJsonObject\b|\bJsonArray\b|\bJsonValueType\b'
$forbiddenWorkloads = @(
    @(
        Join-Path $benchmarkRoot 'src'
        Join-Path $benchmarkRoot 'cswinrt'
        Join-Path $benchmarkRoot 'cppwinrt'
    ) | ForEach-Object {
        Get-ChildItem -LiteralPath $_ -Recurse -File |
            Where-Object {
                $_.Extension -in @('.kt', '.cs', '.cpp', '.h') -and
                $_.FullName -notmatch '[\\/](?:build|bin|obj)[\\/]'
            }
    } | Select-String -Pattern $forbiddenWorkloadPattern -CaseSensitive
)
if ($forbiddenWorkloads.Count -ne 0) {
    $locations = $forbiddenWorkloads | ForEach-Object {
        "$($_.Path):$($_.LineNumber)"
    }
    throw "WinRT JSON surrogate workloads are forbidden; JSON is result transport only: $($locations -join ', ')."
}

$benchmarkClassPattern = '\[MemoryDiagnoser\]\s*(?:public\s+)?class\s+(?<name>[A-Za-z_]\w*)'
$benchmarkMethodPattern = '\[Benchmark(?:\([^\]]*\))?\]\s*public\s+(?:async\s+)?[^\s]+\s+(?<name>[A-Za-z_]\w*)\s*\('
$expected = @(
    Get-ChildItem -LiteralPath $ReferenceDirectory -Filter '*.cs' -File | ForEach-Object {
        $source = Get-Content -LiteralPath $_.FullName -Raw
        $classMatch = [regex]::Match($source, $benchmarkClassPattern)
        if ($classMatch.Success) {
            $family = $classMatch.Groups['name'].Value
            [regex]::Matches($source, $benchmarkMethodPattern) | ForEach-Object {
                "$family.$($_.Groups['name'].Value)"
            }
        }
    }
) | Sort-Object

if ($expected.Count -ne 97) {
    throw "Expected 97 [Benchmark] methods under '$ReferenceDirectory', found $($expected.Count)."
}

$expectedFamilyCounts = [ordered]@{
    QueryInterfacePerf = 27
    EventPerf = 14
    ReflectionPerf = 39
    GuidPerf = 11
    AsyncPerf = 4
    NonAgileObjectPerf = 2
}
foreach ($family in $expectedFamilyCounts.Keys) {
    $actualCount = @($expected | Where-Object { $_.StartsWith("$family.", [StringComparison]::Ordinal) }).Count
    if ($actualCount -ne $expectedFamilyCounts[$family]) {
        throw "Reference family '$family' contains $actualCount scenarios; expected $($expectedFamilyCounts[$family])."
    }
}

$csWinRTProject = Join-Path $benchmarkRoot 'cswinrt\CsWinRTBenchmark.csproj'
[xml]$csWinRTProjectXml = Get-Content -LiteralPath $csWinRTProject -Raw
$linkedReferenceFiles = @(
    $csWinRTProjectXml.Project.ItemGroup.Compile |
        ForEach-Object { [System.IO.Path]::GetFileName([string]$_.Include) } |
        Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
) | Sort-Object -Unique
$expectedReferenceFiles = @(
    Get-ChildItem -LiteralPath $ReferenceDirectory -Filter '*.cs' -File |
        Where-Object {
            [regex]::IsMatch((Get-Content -LiteralPath $_.FullName -Raw), $benchmarkClassPattern)
        } |
        ForEach-Object Name
) | Sort-Object -Unique
$referenceSourceDifference = @(
    Compare-Object -ReferenceObject $expectedReferenceFiles -DifferenceObject $linkedReferenceFiles -CaseSensitive
)
if ($referenceSourceDifference.Count -ne 0) {
    $details = $referenceSourceDifference | ForEach-Object {
        if ($_.SideIndicator -eq '<=') { "not linked $($_.InputObject)" } else { "unexpected link $($_.InputObject)" }
    }
    throw "CsWinRT must compile the benchmark family sources directly from .cswinrt/src/Benchmarks: $($details -join '; ')."
}

$catalogs = [ordered]@{
    'Kotlin/JVM' = $KotlinJvmCatalog
    'Kotlin/Native' = $KotlinNativeCatalog
    'CsWinRT' = $CsWinRTCatalog
    'C++/WinRT' = $CppWinRTCatalog
}

foreach ($runner in $catalogs.Keys) {
    $catalogPath = $catalogs[$runner]
    if (-not (Test-Path -LiteralPath $catalogPath -PathType Leaf)) {
        throw "$runner catalog was not produced at '$catalogPath'."
    }

    $actual = @(
        Get-Content -LiteralPath $catalogPath |
            ForEach-Object { $_.Trim() } |
            Where-Object { $_.Length -ne 0 }
    )
    $duplicates = @(
        $actual | Group-Object -CaseSensitive | Where-Object Count -gt 1 | ForEach-Object Name | Sort-Object
    )
    if ($duplicates.Count -ne 0) {
        throw "$runner catalog contains duplicate scenarios: $($duplicates -join ', ')."
    }

    $difference = @(Compare-Object -ReferenceObject $expected -DifferenceObject ($actual | Sort-Object) -CaseSensitive)
    if ($difference.Count -ne 0) {
        $details = $difference | ForEach-Object {
            if ($_.SideIndicator -eq '<=') { "missing $($_.InputObject)" } else { "unexpected $($_.InputObject)" }
        }
        throw "$runner catalog does not match .cswinrt/src/Benchmarks: $($details -join '; ')."
    }
}

Write-Output "Benchmark catalog parity passed: 6 families and 97 scenarios match on all four runners."
