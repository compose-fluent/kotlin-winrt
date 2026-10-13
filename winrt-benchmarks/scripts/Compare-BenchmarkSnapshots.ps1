#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Before,
    [Parameter(Mandatory)][string]$After,
    [Parameter(Mandatory)][string]$OutputPath
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
function Read-Run([string]$Directory) {
    $run = Get-Content (Join-Path $Directory 'run.json') -Raw | ConvertFrom-Json
    $records = @(Get-Content (Join-Path $Directory 'measurements.jsonl') | ForEach-Object { $_ | ConvertFrom-Json })
    if ($records.Count -ne $run.resultCount -or $records.Count -ne $run.forks * $run.runners.Count * $run.scenarios.Count) {
        throw "Incomplete run: $Directory"
    }
    $groups = @{}
    foreach ($group in ($records | Group-Object { "$($_.runner)/$($_.scenario)" })) {
        if ($group.Count -ne $run.forks -or @($group.Group.fork | Select-Object -Unique).Count -ne $run.forks) {
            throw "Missing or duplicate forks: $($group.Name)"
        }
        $groups[$group.Name] = $group.Group
    }
    return @{ run = $run; groups = $groups }
}
function Median([double[]]$Values) {
    $sorted = @($Values | Sort-Object)
    $middle = [int][Math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2) { return $sorted[$middle] }
    return ($sorted[$middle - 1] + $sorted[$middle]) / 2
}
function Memory-Summary($Records) {
    $memory = @($Records | ForEach-Object {
        if ($_.result.PSObject.Properties['memory']) { $_.result.memory }
    } | Where-Object { $null -ne $_ })
    if ($memory.Count -eq 0) { return $null }
    $allocations = @($memory | Where-Object { $null -ne $_.allocatedBytes } |
        ForEach-Object { $_.allocatedBytes / $_.operations })
    return [pscustomobject]@{
        allocationScopes = @($memory.allocationScope | Select-Object -Unique)
        allocatedBytesPerOp = if ($allocations.Count -eq $memory.Count) { Median $allocations } else { $null }
        allocatedBytesPerOpByFork = $allocations
        heapBeforeBytes = Median @($memory.heapBeforeBytes)
        heapAfterBytes = Median @($memory.heapAfterBytes)
        retainedHeapDeltaBytes = Median @($memory.retainedHeapDeltaBytes)
    }
}
$left = Read-Run $Before
$right = Read-Run $After
if ($left.run.mode -ne $right.run.mode) { throw 'Do not compare timing runs with memory diagnostic runs.' }
if (@(Compare-Object @($left.groups.Keys) @($right.groups.Keys)).Count) { throw 'Scenario/runner sets differ.' }
$rows = foreach ($key in ($left.groups.Keys | Sort-Object)) {
    $a = $left.groups[$key]; $b = $right.groups[$key]
    foreach ($row in @($a) + @($b)) {
        if ($row.result.measurementRounds -ne $a[0].result.measurementRounds -or
            $row.result.warmupRounds -ne $a[0].result.warmupRounds -or
            $row.result.checksum -ne $a[0].result.checksum) { throw "Protocol/checksum mismatch: $key" }
    }
    $beforeNs = Median @($a.result.medianNsPerOp)
    $afterNs = Median @($b.result.medianNsPerOp)
    [pscustomobject]@{ runner = $a[0].runner; scenario = $a[0].scenario
        beforeMedianNsPerOp = $beforeNs; afterMedianNsPerOp = $afterNs; afterOverBefore = $afterNs / $beforeNs
        beforeForkNsPerOp = @($a.result.medianNsPerOp); afterForkNsPerOp = @($b.result.medianNsPerOp)
        beforePeakWorkingSetBytes = Median @($a.sampledPeakWorkingSetBytes)
        afterPeakWorkingSetBytes = Median @($b.sampledPeakWorkingSetBytes)
        beforePeakPrivateBytes = Median @($a.sampledPeakPrivateBytes)
        afterPeakPrivateBytes = Median @($b.sampledPeakPrivateBytes)
        beforeManagedMemory = Memory-Summary $a
        afterManagedMemory = Memory-Summary $b }
}
[pscustomobject]@{ schemaVersion = 1; before = $left.run; after = $right.run; results = @($rows) } |
    ConvertTo-Json -Depth 15 | Set-Content -LiteralPath $OutputPath
