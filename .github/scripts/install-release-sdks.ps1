[CmdletBinding()]
param([string[]] $Versions)
$ErrorActionPreference = 'Stop'
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
  $sdkPath = "C:\Program Files (x86)\Windows Kits\10\References\$version"
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
