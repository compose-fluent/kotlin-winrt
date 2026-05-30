param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptDir

Set-Location -LiteralPath $repoRoot

$quotedArgs = @()
foreach ($arg in $GradleArgs) {
    $quotedArgs += '"' + ($arg -replace '"', '\"') + '"'
}

$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()
$gradleBat = Join-Path $repoRoot "gradlew.bat"
$commandLine = '"' + $gradleBat + '" ' + ($quotedArgs -join ' ') +
    ' 1>"' + $stdoutFile + '" 2>"' + $stderrFile + '"'

try {
    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo.FileName = "cmd.exe"
    $process.StartInfo.Arguments = '/d /c "' + $commandLine + '"'
    $process.StartInfo.WorkingDirectory = $repoRoot
    $process.StartInfo.UseShellExecute = $false
    $process.StartInfo.CreateNoWindow = $true
    [void]$process.Start()
    $process.WaitForExit()

    $exitCode = $process.ExitCode

    if (Test-Path -LiteralPath $stdoutFile) {
        [Console]::Out.Write((Get-Content -LiteralPath $stdoutFile -Raw))
    }
    if (Test-Path -LiteralPath $stderrFile) {
        [Console]::Error.Write((Get-Content -LiteralPath $stderrFile -Raw))
    }

    if ($null -eq $exitCode) {
        $exitCode = 0
    }

    exit $exitCode
} finally {
    Remove-Item -LiteralPath $stdoutFile -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $stderrFile -Force -ErrorAction SilentlyContinue
}
