[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ProjectPath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(Mandatory = $true)]
    [int]$WarmupRounds,

    [Parameter(Mandatory = $true)]
    [int]$MeasurementRounds,

    [Parameter(Mandatory = $true)]
    [int]$Iterations,

    [Parameter(Mandatory = $true)]
    [string]$WindowsSdkVersion,

    [Parameter(Mandatory = $true)]
    [string]$BenchmarkComponentRoot,

    [string]$Filter = "",

    [switch]$ListScenarios
)

$ErrorActionPreference = "Stop"

$programFilesX86 = ${env:ProgramFiles(x86)}
if ([string]::IsNullOrWhiteSpace($programFilesX86)) {
    throw "ProgramFiles(x86) is not available; the C++/WinRT benchmark requires x64 Windows."
}

$windowsSdkRootCandidates = @(
    $env:KOTLIN_WINRT_WINDOWS_SDK_ROOT
    foreach ($registryPath in @(
        "HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows Kits\Installed Roots"
    )) {
        if (Test-Path -LiteralPath $registryPath) {
            (Get-ItemProperty -LiteralPath $registryPath -Name KitsRoot10 -ErrorAction SilentlyContinue).KitsRoot10
        }
    }
    (Join-Path $programFilesX86 "Windows Kits\10")
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

$windowsSdkMatches = @(
    $windowsSdkRootCandidates | Where-Object {
        Test-Path -LiteralPath (Join-Path $_ "Include\$WindowsSdkVersion\um\windows.h") -PathType Leaf
    }
)
if ($windowsSdkMatches.Count -eq 0) {
    throw "Windows SDK $WindowsSdkVersion is not installed. Set KOTLIN_WINRT_WINDOWS_SDK_ROOT for a custom SDK location."
}

$windowsSdkRoot = $windowsSdkMatches[0]
$windowsHeader = Join-Path $windowsSdkRoot "Include\$WindowsSdkVersion\um\windows.h"
if (-not (Test-Path -LiteralPath $windowsHeader -PathType Leaf)) {
    throw "Windows SDK $WindowsSdkVersion is not installed. Install that Windows SDK before running benchmarkCppWinRT."
}

$vswhereCandidates = @(@(
    (Join-Path $programFilesX86 "Microsoft Visual Studio\Installer\vswhere.exe"),
    (Join-Path $env:ProgramFiles "Microsoft Visual Studio\Installer\vswhere.exe")
) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })

if ($vswhereCandidates.Count -eq 0) {
    throw "Visual Studio was not found. Install Visual Studio or Build Tools with 'Desktop development with C++'."
}

$vswhere = $vswhereCandidates[0]
$installationPaths = @(& $vswhere `
    -latest `
    -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath)
if ($LASTEXITCODE -ne 0 -or $installationPaths.Count -eq 0) {
    throw "Visual Studio C++ build tools were not found. Install the MSVC x64 toolset."
}

$installationPath = $installationPaths[0]
$msbuild = Join-Path $installationPath.Trim() "MSBuild\Current\Bin\MSBuild.exe"
if (-not (Test-Path -LiteralPath $msbuild -PathType Leaf)) {
    throw "MSBuild was not found under '$installationPath'."
}

$platformToolsets = @(
    Get-ChildItem -LiteralPath (Join-Path $installationPath.Trim() "MSBuild\Microsoft\VC") -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "Platforms\x64\PlatformToolsets" } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container } |
        ForEach-Object { Get-ChildItem -LiteralPath $_ -Directory -ErrorAction SilentlyContinue } |
        Where-Object { $_.Name -match '^v\d+$' } |
        Sort-Object Name -Descending
)
if ($platformToolsets.Count -eq 0) {
    throw "No x64 MSVC platform toolset was found under '$installationPath'."
}
$platformToolset = $platformToolsets[0].Name

& $msbuild `
    $ProjectPath `
    /nologo `
    /m `
    /t:Build `
    /p:Configuration=Release `
    /p:Platform=x64 `
    "/p:PlatformToolset=$platformToolset" `
    "/p:WindowsTargetPlatformVersion=$WindowsSdkVersion" `
    "/p:BenchmarkComponentRoot=$BenchmarkComponentRoot"
if ($LASTEXITCODE -ne 0) {
    throw "C++/WinRT benchmark build failed with exit code $LASTEXITCODE."
}

$projectDirectory = Split-Path -Parent (Resolve-Path -LiteralPath $ProjectPath)
$executable = Join-Path $projectDirectory "bin\x64\Release\cppwinrt-benchmark.exe"
if (-not (Test-Path -LiteralPath $executable -PathType Leaf)) {
    throw "C++/WinRT benchmark executable was not produced at '$executable'."
}

$benchmarkComponentDll = Join-Path $BenchmarkComponentRoot "BenchmarkComponent.dll"
if (-not (Test-Path -LiteralPath $benchmarkComponentDll -PathType Leaf)) {
    throw "BenchmarkComponent.dll was not produced at '$benchmarkComponentDll'."
}
Copy-Item -LiteralPath $benchmarkComponentDll -Destination (Join-Path (Split-Path -Parent $executable) "BenchmarkComponent.dll") -Force

$runnerArguments = if ($ListScenarios) {
    @("--list-scenarios", "--output", $OutputPath)
} else {
    @(
        "--warmup-rounds", $WarmupRounds,
        "--measurement-rounds", $MeasurementRounds,
        "--iterations", $Iterations,
        "--output", $OutputPath
    )
}
if (-not [string]::IsNullOrWhiteSpace($Filter)) {
    $runnerArguments += @("--filter", $Filter)
}

& $executable @runnerArguments
if ($LASTEXITCODE -ne 0) {
    throw "C++/WinRT benchmark failed with exit code $LASTEXITCODE."
}
