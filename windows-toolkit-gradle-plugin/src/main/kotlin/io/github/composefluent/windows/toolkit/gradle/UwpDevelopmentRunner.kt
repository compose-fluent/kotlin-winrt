package io.github.composefluent.windows.toolkit.gradle

/** OS package registration and activation replace the desktop WinApp CLI launch path for UWP. */
internal val uwpDevelopmentRunnerScript: String = """
param([string]${'$'}Manifest, [string]${'$'}Configuration, [switch]${'$'}StopOnly, [switch]${'$'}NoLaunch, [switch]${'$'}Detach)
${'$'}ErrorActionPreference = 'Stop'
${'$'}document = New-Object System.Xml.XmlDocument
${'$'}document.Load(${'$'}Manifest)
${'$'}identity = ${'$'}document.Package.Identity
${'$'}application = @(${'$'}document.Package.Applications.Application)[0]
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace KotlinWinRT.Uwp {
    [ComImport, Guid("2E941141-7F97-4756-BA1D-9DECDE894A3D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IApplicationActivationManager {
        [PreserveSig] int ActivateApplication([MarshalAs(UnmanagedType.LPWStr)] string appUserModelId,
            [MarshalAs(UnmanagedType.LPWStr)] string arguments, uint options, out uint processId);
        [PreserveSig] int ActivateForFile([MarshalAs(UnmanagedType.LPWStr)] string appUserModelId,
            IntPtr items, [MarshalAs(UnmanagedType.LPWStr)] string verb, out uint processId);
        [PreserveSig] int ActivateForProtocol([MarshalAs(UnmanagedType.LPWStr)] string appUserModelId,
            IntPtr items, out uint processId);
    }
    [ComImport, Guid("45BA127D-10A8-46EA-8AB7-56EA9078943C")]
    class ApplicationActivationManager { }
    // shobjidl_core.h: preserve the vtable slots preceding TerminateAllProcesses.
    [ComImport, Guid("F27C3930-8029-4AD1-94E3-3DBA417810C1"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPackageDebugSettings {
        [PreserveSig] int EnableDebugging([MarshalAs(UnmanagedType.LPWStr)] string packageFullName,
            [MarshalAs(UnmanagedType.LPWStr)] string debuggerCommandLine, IntPtr environment);
        [PreserveSig] int DisableDebugging([MarshalAs(UnmanagedType.LPWStr)] string packageFullName);
        [PreserveSig] int Suspend([MarshalAs(UnmanagedType.LPWStr)] string packageFullName);
        [PreserveSig] int Resume([MarshalAs(UnmanagedType.LPWStr)] string packageFullName);
        [PreserveSig] int TerminateAllProcesses([MarshalAs(UnmanagedType.LPWStr)] string packageFullName);
    }
    [ComImport, Guid("B1AEC16F-2383-4852-B0E9-8F0B1DC66B4D")]
    class PackageDebugSettings { }
    public static class Launcher {
        public static void Terminate(string packageFullName) {
            var settings = (IPackageDebugSettings)new PackageDebugSettings();
            try { Marshal.ThrowExceptionForHR(settings.TerminateAllProcesses(packageFullName)); }
            finally { Marshal.FinalReleaseComObject(settings); }
        }
        public static uint Activate(string id, string arguments) {
            var manager = (IApplicationActivationManager)new ApplicationActivationManager();
            try {
                uint processId;
                Marshal.ThrowExceptionForHR(manager.ActivateApplication(id, arguments, 0, out processId));
                return processId;
            } finally { Marshal.FinalReleaseComObject(manager); }
        }
    }
}
'@
function Get-DevelopmentPackage {
    ${'$'}registeredMatches = @(Get-AppxPackage -Name ${'$'}identity.Name | Where-Object {
        ${'$'}_.Name -eq ${'$'}identity.Name -and ${'$'}_.Publisher -eq ${'$'}identity.Publisher
    })
    if (${'$'}registeredMatches.Count -gt 1) { throw "Expected at most one registered package for ${'$'}(${'$'}identity.Name)." }
    if (${'$'}registeredMatches.Count -eq 1) {
        if (-not ${'$'}registeredMatches[0].IsDevelopmentMode) {
            throw "Refusing to replace non-development package ${'$'}(${'$'}registeredMatches[0].PackageFullName)."
        }
        return ${'$'}registeredMatches[0]
    }
}
${'$'}previousPackage = Get-DevelopmentPackage
${'$'}snapshotPath = ${'$'}Configuration + '.registration.json'
if (${'$'}StopOnly) {
    # Stop only this registered development package before Gradle replaces its mapped binaries.
    ${'$'}snapshot = @{}
    if (${'$'}previousPackage) {
        ${'$'}previousManifest = Join-Path ${'$'}previousPackage.InstallLocation 'AppxManifest.xml'
        ${'$'}snapshot = @{
            packageFullName = ${'$'}previousPackage.PackageFullName
            manifest = ${'$'}previousManifest
            contents = [Convert]::ToBase64String([IO.File]::ReadAllBytes(${'$'}previousManifest))
        }
        [KotlinWinRT.Uwp.Launcher]::Terminate(${'$'}previousPackage.PackageFullName)
    }
    # Keep the original manifest outside the layout Gradle is about to replace.
    [IO.File]::WriteAllText(${'$'}snapshotPath, (${'$'}snapshot | ConvertTo-Json -Compress))
    exit 0
}
${'$'}configurationData = Get-Content -LiteralPath ${'$'}Configuration -Raw -Encoding UTF8 | ConvertFrom-Json
foreach (${'$'}dependency in ${'$'}configurationData.dependencyPackages) {
    # Framework dependencies are minimum versions. Never downgrade a compatible installed copy.
    ${'$'}installed = @(Get-AppxPackage -Name ${'$'}dependency.name | Where-Object {
        ${'$'}_.Publisher -eq ${'$'}dependency.publisher -and
        ([string]${'$'}_.Architecture -eq ${'$'}dependency.architecture -or [string]${'$'}_.Architecture -eq 'neutral') -and
        [version]${'$'}_.Version -ge [version]${'$'}dependency.version
    })
    if (${'$'}installed.Count -eq 0) { Add-AppxPackage -Path ${'$'}dependency.path -ForceApplicationShutdown }
}
${'$'}previousManifest = ${'$'}null
${'$'}previousContents = ${'$'}null
if (${'$'}previousPackage) {
    ${'$'}previousManifest = Join-Path ${'$'}previousPackage.InstallLocation 'AppxManifest.xml'
    ${'$'}previousContents = [IO.File]::ReadAllBytes(${'$'}previousManifest)
    if (Test-Path -LiteralPath ${'$'}snapshotPath) {
        ${'$'}snapshot = Get-Content -LiteralPath ${'$'}snapshotPath -Raw -Encoding UTF8 | ConvertFrom-Json
        if (${'$'}snapshot.packageFullName -eq ${'$'}previousPackage.PackageFullName -and ${'$'}snapshot.manifest -eq ${'$'}previousManifest) {
            ${'$'}previousContents = [Convert]::FromBase64String(${'$'}snapshot.contents)
        }
    }
}
${'$'}removed = ${'$'}false
try {
    # Register alone can retain an old location or old extensions for the same identity/version.
    # PreserveApplicationData is supported specifically for loose development registrations.
    if (${'$'}previousPackage) {
        Remove-AppxPackage -Package ${'$'}previousPackage.PackageFullName -PreserveApplicationData
        ${'$'}removed = ${'$'}true
    }
    Add-AppxPackage -Register -Path ${'$'}Manifest -ForceApplicationShutdown
    ${'$'}package = Get-DevelopmentPackage
    if (-not ${'$'}package) { throw "Package registration did not return ${'$'}(${'$'}identity.Name)." }
    ${'$'}expectedLocation = [IO.Path]::GetFullPath((Split-Path -Parent ${'$'}Manifest)).TrimEnd('\', '/')
    ${'$'}actualLocation = [IO.Path]::GetFullPath(${'$'}package.InstallLocation).TrimEnd('\', '/')
    if (-not [string]::Equals(${'$'}actualLocation, ${'$'}expectedLocation, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Package registration retained '${'$'}actualLocation' instead of '${'$'}expectedLocation'."
    }
} catch {
    ${'$'}registrationFailure = ${'$'}_
    if (${'$'}removed -and ${'$'}previousContents) {
        try {
            [IO.File]::WriteAllBytes(${'$'}previousManifest, ${'$'}previousContents)
            Add-AppxPackage -Register -Path ${'$'}previousManifest -ForceApplicationShutdown
        } catch { Write-Warning "Could not restore the previous development registration: ${'$'}_" }
    }
    throw ${'$'}registrationFailure
}
if (${'$'}NoLaunch) { exit 0 }
${'$'}applicationId = ${'$'}package.PackageFamilyName + '!' + ${'$'}application.Id
${'$'}applicationProcessId = [KotlinWinRT.Uwp.Launcher]::Activate(${'$'}applicationId, [string]${'$'}configurationData.applicationArguments)
Write-Output "Activated ${'$'}applicationId (PID ${'$'}applicationProcessId)"
if (-not ${'$'}Detach) {
    ${'$'}applicationProcess = Get-Process -Id ${'$'}applicationProcessId -ErrorAction SilentlyContinue
    if (${'$'}applicationProcess) {
        # Get-Process does not retain a native handle by default. Hold one while waiting so
        # Windows PowerShell can still read the exit status after the process disappears.
        ${'$'}null = ${'$'}applicationProcess.Handle
        ${'$'}applicationProcess.WaitForExit()
        exit ${'$'}applicationProcess.ExitCode
    }
}
""".trimIndent()
