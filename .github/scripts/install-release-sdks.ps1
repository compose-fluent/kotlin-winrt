[CmdletBinding()]
param([string[]] $Versions, [string] $SdkRoot)
$ErrorActionPreference = 'Stop'
if (-not $SdkRoot) { $SdkRoot = $env:WINDOWS_KITS_10_DIR }
if (-not $SdkRoot) {
  $SdkRoot = Get-ItemPropertyValue 'HKLM:\SOFTWARE\Microsoft\Windows Kits\Installed Roots' `
    -Name KitsRoot10 -ErrorAction SilentlyContinue
}
if (-not $SdkRoot) { $SdkRoot = 'C:\Program Files (x86)\Windows Kits\10' }
$SdkRoot = [IO.Path]::GetFullPath($SdkRoot)
if (-not $Versions) {
    $line = Get-Content (Join-Path $PSScriptRoot '../../gradle.properties') | Where-Object { $_ -match '^kotlinWinRT.projections.windowsSdkVersions=' }
    $Versions = ($line -split '=', 2)[1].Split(',')
}
function Install-WindowsSdkBaseline([string] $version) {
  $packageVersion = $version -replace '\.0$', ''
  $packageId = "Microsoft.WindowsSDK.$packageVersion"
  $winget = Get-Command winget -ErrorAction SilentlyContinue
  if ($winget) {
    Write-Host "Installing $packageId for Windows SDK $version via winget"
    winget install --id $packageId `
      --silent `
      --accept-source-agreements `
      --accept-package-agreements
    if ($LASTEXITCODE -ne 0) {
      Write-Host "winget exited with code $LASTEXITCODE for $packageId (may indicate already installed)"
    }
    return
  }

  $installerUrl = switch ($version) {
    "10.0.19041.0" { "https://go.microsoft.com/fwlink/?linkid=2120843" }
    "10.0.22000.0" { "https://go.microsoft.com/fwlink/?linkid=2173743" }
    "10.0.22621.0" { "https://go.microsoft.com/fwlink/?linkid=2196241" }
    "10.0.26100.0" { "https://go.microsoft.com/fwlink/?linkid=2324781" }
    default { "" }
  }
  if (-not $installerUrl) {
    Write-Error "No Windows SDK installer mapping is configured for Windows SDK $version, and winget is unavailable."
    exit 1
  }
  $installer = Join-Path $env:RUNNER_TEMP "winsdksetup-$packageVersion.exe"
  Write-Host "Downloading Windows SDK $version installer from $installerUrl"
  Invoke-WebRequest -Uri $installerUrl -OutFile $installer
  Write-Host "Installing Windows SDK $version via standalone installer"
  $process = Start-Process `
    -FilePath $installer `
    -ArgumentList @(
      "/features",
      "OptionId.UWPManaged",
      "OptionId.UWPCPP",
      "OptionId.UWPLocalized",
      "/quiet",
      "/norestart"
    ) `
    -WindowStyle Hidden `
    -Wait `
    -PassThru
  if ($process.ExitCode -ne 0 -and $process.ExitCode -ne 3010) {
    Write-Error "Windows SDK standalone installer exited with code $($process.ExitCode) while installing $version."
    exit $process.ExitCode
  }
}
foreach ($version in $versions) {
  $sdkPath = Join-Path $SdkRoot "References/$version"
  if (Test-Path $sdkPath) {
    Write-Host "Windows SDK $version already installed at $sdkPath"
    continue
  }
  Install-WindowsSdkBaseline $version
  if (-not (Test-Path $sdkPath)) {
    Write-Error "Windows SDK $version was not found at $sdkPath after installation."
    exit 1
  }
}
# GitHub's PowerShell wrapper exits with the last native command's code. An
# already-installed result (or reboot-required installer result) is successful
# once every requested SDK was verified above.
$global:LASTEXITCODE = 0
