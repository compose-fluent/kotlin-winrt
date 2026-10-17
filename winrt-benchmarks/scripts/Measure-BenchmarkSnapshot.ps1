#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Snapshot,
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateRange(1, 20)][int]$Forks = 3,
    [string[]]$Runners = @('kotlin-native', 'cppwinrt', 'cswinrt', 'kotlin-jvm'),
    [string[]]$Scenarios = @(),
    [ValidateRange(0, 10000)][int]$WarmupRounds = 8,
    [ValidateRange(1, 100000)][int]$MinimumIterations,
    [ValidateRange(1, 10000)][int]$MeasurementRounds = 25,
    [ValidateRange(1, 3600)][int]$TimeoutSeconds = 180,
    [switch]$Memory,
    [switch]$NativeGcDiagnostics
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($Memory -and $NativeGcDiagnostics) { throw 'Run heap-retention and Native GC diagnostics separately.' }
if ($NativeGcDiagnostics -and 'kotlin-native' -notin $Runners) { throw 'Native GC diagnostics require kotlin-native in Runners.' }
$Snapshot = (Resolve-Path -LiteralPath $Snapshot).Path
$manifest = Get-Content (Join-Path $Snapshot 'snapshot.json') -Raw | ConvertFrom-Json
foreach ($runner in $manifest.runners) {
    if (!(Test-Path -LiteralPath $runner.executable -PathType Leaf)) {
        throw "Snapshot executable is missing (the snapshot may have been cleaned). Rebuild it to run again: $($runner.executable)"
    }
}
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Use a new output directory; previous measurements are immutable.' }
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
[IO.Directory]::CreateDirectory($OutputDirectory) | Out-Null
$catalog = @(Get-Content (Join-Path $Snapshot 'catalogs/kotlin-native.txt') | Where-Object { $_ })
foreach ($name in $Scenarios) { if ($name -notin $catalog) { throw "Unknown scenario: $name" } }
foreach ($name in $Runners) { if ($name -notin $manifest.runners.name) { throw "Unknown runner: $name" } }
if ($Scenarios.Count -eq 0) { $Scenarios = $catalog }
$records = [Collections.Generic.List[object]]::new()
for ($fork = 1; $fork -le $Forks; $fork++) {
    $order = @($Runners)
    if ($fork % 2 -eq 0) { [array]::Reverse($order) }
    foreach ($scenario in $Scenarios) {
        foreach ($name in $order) {
            $runner = $manifest.runners | Where-Object name -EQ $name
            $stem = "$name-$scenario-$fork"
            $resultPath = Join-Path $OutputDirectory "$stem.jsonl"
            $start = [Diagnostics.ProcessStartInfo]::new([string]$runner.executable)
            $start.WorkingDirectory = Split-Path -Parent $runner.executable
            $start.UseShellExecute = $false
            $start.CreateNoWindow = $true
            $start.RedirectStandardOutput = $true
            $start.RedirectStandardError = $true
            foreach ($arg in $runner.arguments) { $start.ArgumentList.Add([string]$arg) }
            if ($PSBoundParameters.ContainsKey('MinimumIterations')) {
                $start.ArgumentList.Add('--iterations')
                $start.ArgumentList.Add([string]$MinimumIterations)
            }
            foreach ($arg in @('--filter', $scenario, '--warmup-rounds', [string]$WarmupRounds, '--measurement-rounds',
                    [string]$MeasurementRounds, '--output', $resultPath)) { $start.ArgumentList.Add($arg) }
            if ($Memory -and $runner.managedMemory) { $start.ArgumentList.Add('--memory') }
            if ($NativeGcDiagnostics -and $name -eq 'kotlin-native') {
                $start.ArgumentList.Add('--native-gc-diagnostics')
            }
            foreach ($property in $runner.environment.PSObject.Properties) {
                $start.Environment[$property.Name] = [string]$property.Value
            }
            $process = [Diagnostics.Process]::new()
            $process.StartInfo = $start
            $samples = [Collections.Generic.List[object]]::new()
            $watch = [Diagnostics.Stopwatch]::StartNew()
            $started = $false
            try {
                if (-not $process.Start()) { throw "Could not start $name" }
                $started = $true
                $stdout = $process.StandardOutput.ReadToEndAsync()
                $stderr = $process.StandardError.ReadToEndAsync()
                while (-not $process.WaitForExit(50)) {
                    if ($watch.Elapsed.TotalSeconds -gt $TimeoutSeconds) { throw "Timeout: $stem" }
                    $process.Refresh()
                    $samples.Add([pscustomobject]@{ milliseconds = $watch.Elapsed.TotalMilliseconds
                        workingSetBytes = $process.WorkingSet64; privateBytes = $process.PrivateMemorySize64 })
                }
                $watch.Stop()
                $stdout.GetAwaiter().GetResult() | Set-Content (Join-Path $OutputDirectory "$stem.stdout.txt")
                $stderr.GetAwaiter().GetResult() | Set-Content (Join-Path $OutputDirectory "$stem.stderr.txt")
                if ($process.ExitCode -ne 0) { throw "Runner failed ($($process.ExitCode)): $stem" }
                $result = @(Get-Content -LiteralPath $resultPath | ForEach-Object { $_ | ConvertFrom-Json })
                if ($result.Count -ne 1 -or $result[0].scenario -ne $scenario) { throw "Wrong result: $stem" }
                $record = [pscustomobject]@{ runner = $name; scenario = $scenario; fork = $fork
                    elapsedMilliseconds = $watch.Elapsed.TotalMilliseconds; result = $result[0]
                    processMemoryScope = 'whole-process-including-startup-and-runtime; sampled-every-50ms'
                    sampledPeakWorkingSetBytes = ($samples.workingSetBytes | Measure-Object -Maximum).Maximum
                    sampledPeakPrivateBytes = ($samples.privateBytes | Measure-Object -Maximum).Maximum
                    samples = @($samples.ToArray()) }
                $records.Add($record)
                $record | ConvertTo-Json -Depth 12 -Compress | Add-Content (Join-Path $OutputDirectory 'measurements.jsonl')
                Write-Host "$fork/$Forks $name $scenario : $($result[0].medianNsPerOp) ns/op"
            } finally {
                if ($started -and -not $process.HasExited) { $process.Kill($true); $process.WaitForExit() }
                $process.Dispose()
            }
        }
    }
}
[pscustomobject]@{ schemaVersion = 1; snapshot = $manifest; mode = $(if ($NativeGcDiagnostics) {'native-gc-diagnostic'} elseif ($Memory) {'memory-diagnostic'} else {'timing'})
    forks = $Forks; scenarios = $Scenarios; runners = $Runners; resultCount = $records.Count
    warmupRounds = $WarmupRounds; measurementRounds = $MeasurementRounds
    minimumIterationsOverride = $(if ($PSBoundParameters.ContainsKey('MinimumIterations')) { $MinimumIterations } else { $null })
    hostInfo = [pscustomobject]@{ os = [Environment]::OSVersion.VersionString; processors = [Environment]::ProcessorCount
        powershell = $PSVersionTable.PSVersion.ToString() } } |
    ConvertTo-Json -Depth 12 | Set-Content (Join-Path $OutputDirectory 'run.json')
