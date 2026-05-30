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

$commandLine = "call gradlew.bat " + ($quotedArgs -join ' ')
$stdoutFile = [System.IO.Path]::GetTempFileName()
$stderrFile = [System.IO.Path]::GetTempFileName()

try {
    $process = Start-Process `
        -FilePath "cmd.exe" `
        -ArgumentList "/d", "/s", "/c", $commandLine `
        -WorkingDirectory $repoRoot `
        -RedirectStandardOutput $stdoutFile `
        -RedirectStandardError $stderrFile `
        -WindowStyle Hidden `
        -Wait `
        -PassThru

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
