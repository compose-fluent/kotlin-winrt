[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$WindowsSdkVersion,

    [string]$SourceDirectory = "",

    [string]$TestWinRTCommit = "aa4edcd52542cfe036473d008d3f66c8a220d59d"
)

$ErrorActionPreference = "Stop"

function Invoke-Checked {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,

        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList
    )

    & $FilePath @ArgumentList
    if ($LASTEXITCODE -ne 0) {
        throw "'$FilePath' failed with exit code $LASTEXITCODE."
    }
}

$outputRoot = [System.IO.Path]::GetFullPath($OutputDirectory)
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

if ([string]::IsNullOrWhiteSpace($SourceDirectory)) {
    $sourceRoot = Join-Path $outputRoot "TestWinRT-$($TestWinRTCommit.Substring(0, 8))"
    if (-not (Test-Path -LiteralPath (Join-Path $sourceRoot ".git") -PathType Container)) {
        Invoke-Checked -FilePath "git" -ArgumentList @(
            "clone",
            "--filter=blob:none",
            "--no-checkout",
            "https://github.com/microsoft/TestWinRT.git",
            $sourceRoot
        )
        Invoke-Checked -FilePath "git" -ArgumentList @(
            "-c",
            "safe.directory=$sourceRoot",
            "-C",
            $sourceRoot,
            "checkout",
            "--detach",
            $TestWinRTCommit
        )
    }
} else {
    $sourceRoot = [System.IO.Path]::GetFullPath($SourceDirectory)
}

$componentProject = Join-Path $sourceRoot "BenchmarkComponent\BenchmarkComponent.vcxproj"
$packagesConfig = Join-Path $sourceRoot "BenchmarkComponent\packages.config"
if (-not (Test-Path -LiteralPath $componentProject -PathType Leaf)) {
    throw "TestWinRT BenchmarkComponent project was not found at '$componentProject'."
}
if (-not (Test-Path -LiteralPath $packagesConfig -PathType Leaf)) {
    throw "TestWinRT BenchmarkComponent packages.config was not found at '$packagesConfig'."
}

if (Test-Path -LiteralPath (Join-Path $sourceRoot ".git") -PathType Container) {
    $actualCommit = (& git -c "safe.directory=$sourceRoot" -C $sourceRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or $actualCommit -ne $TestWinRTCommit) {
        throw "TestWinRT source must be at $TestWinRTCommit; found '$actualCommit'."
    }
}

$toolsDirectory = Join-Path $outputRoot "tools"
New-Item -ItemType Directory -Path $toolsDirectory -Force | Out-Null
$nuget = Join-Path $toolsDirectory "nuget.exe"
if (-not (Test-Path -LiteralPath $nuget -PathType Leaf)) {
    Invoke-WebRequest -Uri "https://dist.nuget.org/win-x86-commandline/latest/nuget.exe" -OutFile $nuget
}

$packagesDirectory = Join-Path $sourceRoot "packages"
Invoke-Checked -FilePath $nuget -ArgumentList @(
    "restore",
    $packagesConfig,
    "-PackagesDirectory",
    $packagesDirectory,
    "-NonInteractive"
)

$programFilesX86 = ${env:ProgramFiles(x86)}
if ([string]::IsNullOrWhiteSpace($programFilesX86)) {
    throw "ProgramFiles(x86) is unavailable; BenchmarkComponent requires x64 Windows."
}
$vswhereCandidates = @(@(
        (Join-Path $programFilesX86 "Microsoft Visual Studio\Installer\vswhere.exe"),
        (Join-Path $env:ProgramFiles "Microsoft Visual Studio\Installer\vswhere.exe")
    ) | Where-Object { Test-Path -LiteralPath $_ -PathType Leaf })
if ($vswhereCandidates.Count -eq 0) {
    throw "Visual Studio was not found. Install the x64 C++ build tools."
}

$installationPaths = @(& $vswhereCandidates[0] `
    -latest `
    -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath)
if ($LASTEXITCODE -ne 0 -or $installationPaths.Count -eq 0) {
    throw "Visual Studio C++ build tools were not found."
}
$installationPath = $installationPaths[0].Trim()
$msbuild = Join-Path $installationPath "MSBuild\Current\Bin\MSBuild.exe"
if (-not (Test-Path -LiteralPath $msbuild -PathType Leaf)) {
    throw "MSBuild was not found at '$msbuild'."
}

$platformToolsets = @(
    Get-ChildItem -LiteralPath (Join-Path $installationPath "MSBuild\Microsoft\VC") -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName "Platforms\x64\PlatformToolsets" } |
        Where-Object { Test-Path -LiteralPath $_ -PathType Container } |
        ForEach-Object { Get-ChildItem -LiteralPath $_ -Directory -ErrorAction SilentlyContinue } |
        Where-Object { $_.Name -match '^v\d+$' } |
        Sort-Object Name -Descending
)
if ($platformToolsets.Count -eq 0) {
    throw "No x64 MSVC platform toolset was found under '$installationPath'."
}

$oldCl = $env:CL
try {
    $clOptions = @(
        $oldCl,
        "/D_SILENCE_EXPERIMENTAL_COROUTINE_DEPRECATION_WARNINGS"
    ) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    $env:CL = [string]::Join(" ", $clOptions)

    Invoke-Checked -FilePath $msbuild -ArgumentList @(
        $componentProject,
        "/nologo",
        "/m",
        "/t:Build",
        "/p:Configuration=Release",
        "/p:Platform=x64",
        "/p:PlatformToolset=$($platformToolsets[0].Name)",
        "/p:WindowsTargetPlatformVersion=$WindowsSdkVersion"
    )
} finally {
    $env:CL = $oldCl
}

$componentBuildOutput = Join-Path $sourceRoot "BenchmarkComponent\x64\Release\BenchmarkComponent"
$componentDll = Join-Path $componentBuildOutput "BenchmarkComponent.dll"
$componentWinmd = Join-Path $componentBuildOutput "BenchmarkComponent.winmd"
$generatedFiles = Join-Path $sourceRoot "BenchmarkComponent\Generated Files"
foreach ($required in @($componentDll, $componentWinmd, $generatedFiles)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "BenchmarkComponent build did not produce '$required'."
    }
}

Copy-Item -LiteralPath $componentDll -Destination (Join-Path $outputRoot "BenchmarkComponent.dll") -Force
Copy-Item -LiteralPath $componentWinmd -Destination (Join-Path $outputRoot "BenchmarkComponent.winmd") -Force
$referenceIncludeDirectory = Join-Path $outputRoot "cppwinrt-reference-include"
New-Item -ItemType Directory -Path $referenceIncludeDirectory -Force | Out-Null
Copy-Item -Path (Join-Path $generatedFiles "*") -Destination $referenceIncludeDirectory -Recurse -Force
$cppWinRT = Join-Path $packagesDirectory "Microsoft.Windows.CppWinRT.2.0.201113.7\bin\cppwinrt.exe"
if (-not (Test-Path -LiteralPath $cppWinRT -PathType Leaf)) {
    throw "The pinned C++/WinRT projection tool was not restored at '$cppWinRT'."
}
$consumerIncludeDirectory = Join-Path $outputRoot "cppwinrt-include"
Invoke-Checked -FilePath $cppWinRT -ArgumentList @(
    "-input",
    $componentWinmd,
    "-reference",
    $WindowsSdkVersion,
    "-output",
    $consumerIncludeDirectory,
    "-overwrite"
)
Set-Content -LiteralPath (Join-Path $outputRoot "TestWinRT.commit") -Value $TestWinRTCommit -Encoding ascii
