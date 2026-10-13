#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BuildTask,
    [Parameter(Mandatory)][string]$CleanTask,
    [Parameter(Mandatory)][string]$ProbeFile,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 10)][int]$Rounds = 3,
    [string[]]$GradleArguments = @()
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$ProbeFile = [IO.Path]::GetFullPath($ProbeFile)
if (-not $ProbeFile.StartsWith($repo + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    [IO.Path]::GetExtension($ProbeFile) -ne '.kt' -or (Test-Path -LiteralPath $ProbeFile)) {
    throw 'ProbeFile must be a new .kt file within a compiled source set in this repository.'
}
if (-not (Test-Path -LiteralPath (Split-Path -Parent $ProbeFile))) { throw 'Probe source directory does not exist.' }
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new output directory.' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$records = [Collections.Generic.List[object]]::new()
function Invoke-MeasuredBuild([string]$Mode, [int]$Round, [string[]]$Tasks) {
    $log = Join-Path $OutputDirectory "$Round-$Mode.log"
    $watch = [Diagnostics.Stopwatch]::StartNew()
    & (Join-Path $repo 'gradlew.bat') @Tasks --console=plain --no-build-cache --no-configuration-cache --profile @GradleArguments *> $log
    $watch.Stop()
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $log" }
    if ($Mode -ne 'clean') {
        $records.Add([pscustomobject]@{ mode = $Mode; round = $Round; milliseconds = $watch.Elapsed.TotalMilliseconds
            task = $BuildTask; log = $log; taskOutcomes = @(Get-Content $log | Where-Object {$_ -like '> Task *'}) })
    }
}
Push-Location $repo
try {
    [IO.File]::WriteAllText($ProbeFile, "package io.github.composefluent.winrt.benchmarks.compileprobe`ninternal fun compilationProbeValue(): Int = 0`n")
    for ($round = 1; $round -le $Rounds; $round++) {
        Invoke-MeasuredBuild 'clean' $round @($CleanTask)
        Invoke-MeasuredBuild 'full' $round @($BuildTask)
        Invoke-MeasuredBuild 'no-change' $round @($BuildTask)
        [IO.File]::WriteAllText($ProbeFile, "package io.github.composefluent.winrt.benchmarks.compileprobe`ninternal fun compilationProbeValue(): Int = $round`n")
        Invoke-MeasuredBuild 'single-file-body-edit' $round @($BuildTask)
    }
} finally {
    # Only this newly created, validated file is removed; existing user sources are never edited.
    if (Test-Path -LiteralPath $ProbeFile) { Remove-Item -LiteralPath $ProbeFile }
    Pop-Location
    [pscustomobject]@{ schemaVersion = 1; scope = 'wall time; daemon reused when available; selected module clean; dependency caches retained'
        complete = ($records.Count -eq $Rounds * 3); rounds = $Rounds
        buildTask = $BuildTask; cleanTask = $CleanTask; gradleArguments = $GradleArguments; measurements = $records.ToArray() } |
        ConvertTo-Json -Depth 8 | Set-Content (Join-Path $OutputDirectory 'compilation.json')
}
