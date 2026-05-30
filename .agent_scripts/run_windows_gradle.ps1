$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

Set-Location -LiteralPath $repoRoot

$gradleArgs = @()
for ($i = 0; $i -lt $args.Count; $i++) {
    $arg = [string]$args[$i]
    if (($arg -match '^-[PD][^.=]+$') -and ($i + 1 -lt $args.Count)) {
        $next = [string]$args[$i + 1]
        if ($next.StartsWith(".")) {
            $arg += $next
            $i++
        }
    }
    $gradleArgs += $arg
}

$gradleBat = Join-Path $repoRoot "gradlew.bat"

$quotedArgs = @()
$quotedArgs += '"' + ($gradleBat -replace '"', '\"') + '"'
foreach ($arg in $gradleArgs) {
    $quotedArgs += '"' + ($arg -replace '"', '\"') + '"'
}
$commandLine = $quotedArgs -join ' '

$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()

function Write-NewFileContent {
    param(
        [string]$Path,
        [ref]$Offset,
        [System.IO.TextWriter]$Writer
    )

    if (-not (Test-Path -LiteralPath $Path)) {
        return
    }

    $stream = $null
    try {
        $stream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Open, [System.IO.FileAccess]::Read, [System.IO.FileShare]::ReadWrite)
        if ($stream.Length -le $Offset.Value) {
            return
        }

        [void]$stream.Seek($Offset.Value, [System.IO.SeekOrigin]::Begin)
        $length = [int]($stream.Length - $Offset.Value)
        $buffer = [byte[]]::new($length)
        $read = $stream.Read($buffer, 0, $buffer.Length)
        if ($read -gt 0) {
            $Writer.Write([System.Text.Encoding]::Default.GetString($buffer, 0, $read))
            $Offset.Value += $read
        }
    } finally {
        if ($null -ne $stream) {
            $stream.Dispose()
        }
    }
}

try {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "cmd.exe"
    $process.StartInfo.Arguments = '/d /s /c "' + $commandLine + ' 1>"' + $stdoutFile + '" 2>"' + $stderrFile + '""'
    $process.StartInfo.WorkingDirectory = $repoRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true

    [void]$process.Start()
    $stdoutOffset = 0L
    $stderrOffset = 0L
    while (-not $process.WaitForExit(1000)) {
        Write-NewFileContent -Path $stdoutFile -Offset ([ref]$stdoutOffset) -Writer ([Console]::Out)
        Write-NewFileContent -Path $stderrFile -Offset ([ref]$stderrOffset) -Writer ([Console]::Error)
    }

    Write-NewFileContent -Path $stdoutFile -Offset ([ref]$stdoutOffset) -Writer ([Console]::Out)
    Write-NewFileContent -Path $stderrFile -Offset ([ref]$stderrOffset) -Writer ([Console]::Error)
    $process.WaitForExit()
    $exitCode = $process.ExitCode

    if ($null -eq $exitCode) {
        $exitCode = 0
    }

    exit $exitCode
} finally {
    if ($null -ne $process) {
        $process.Dispose()
    }
    Remove-Item -LiteralPath $stdoutFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stderrFile -Force -ErrorAction SilentlyContinue
}
