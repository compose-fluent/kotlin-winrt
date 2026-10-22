package io.github.composefluent.windows.toolkit.gradle

/** OS package registration and activation replace the desktop WinApp CLI launch path for UWP. */
internal val uwpDevelopmentRunnerScript: String = """
param([string]${'$'}Manifest, [string]${'$'}Configuration, [switch]${'$'}StopOnly, [switch]${'$'}NoLaunch, [switch]${'$'}Detach,
    [string]${'$'}WatchPackage, [int]${'$'}WatchProcess, [long]${'$'}WatchStart, [string]${'$'}OwnerFile, [string]${'$'}Owner,
    [switch]${'$'}ResumeActivation, [int]${'$'}p, [int]${'$'}tid)
${'$'}ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.UTF8Encoding]::new(${'$'}false)
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace KotlinWinRT.UWP {
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
        [DllImport("kernel32.dll", SetLastError = true)] static extern IntPtr OpenThread(uint access, bool inherit, uint id);
        [DllImport("kernel32.dll", SetLastError = true)] static extern uint GetProcessIdOfThread(IntPtr thread);
        [DllImport("kernel32.dll", SetLastError = true)] static extern uint ResumeThread(IntPtr thread);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        public static void ResumeActivation(uint processId, uint threadId) {
            var thread = OpenThread(0x802, false, threadId); // query limited information + suspend/resume
            if (thread == IntPtr.Zero) throw new System.ComponentModel.Win32Exception();
            try {
                if (GetProcessIdOfThread(thread) != processId) throw new InvalidOperationException("Activation thread does not belong to the activated process.");
                if (ResumeThread(thread) == UInt32.MaxValue) throw new System.ComponentModel.Win32Exception();
            } finally { CloseHandle(thread); }
        }
        // EnableDebugging accepts a double-NUL terminated environment block. Keep package
        // debug mode scoped to this activation; an older process must not clear a newer lease.
        public static void DevelopmentEnvironment(string package, string ownerFile, string owner, string[] environment) {
            DevelopmentEnvironment(package, ownerFile, owner, environment, null);
        }
        public static void DevelopmentEnvironment(string package, string ownerFile, string owner, string[] environment, string debugger) {
            string key;
            using (var hash = System.Security.Cryptography.SHA256.Create()) {
                key = BitConverter.ToString(hash.ComputeHash(System.Text.Encoding.UTF8.GetBytes(package))).Replace("-", "");
            }
            using (var mutex = new System.Threading.Mutex(false, "Local\\KotlinWinRT.Development." + key)) {
                try { mutex.WaitOne(); } catch (System.Threading.AbandonedMutexException) { }
                try {
                    var settings = (IPackageDebugSettings)new PackageDebugSettings();
                    try {
                        if (environment != null) {
                            var block = Marshal.StringToHGlobalUni(String.Join("\0", environment) + "\0\0");
                            try {
                                System.IO.File.WriteAllText(ownerFile, owner);
                                Marshal.ThrowExceptionForHR(settings.EnableDebugging(package, debugger, block));
                            } finally { Marshal.FreeHGlobal(block); }
                        } else if (System.IO.File.Exists(ownerFile) && System.IO.File.ReadAllText(ownerFile) == owner) {
                            Marshal.ThrowExceptionForHR(settings.DisableDebugging(package));
                            System.IO.File.Delete(ownerFile);
                        }
                    } finally { Marshal.FinalReleaseComObject(settings); }
                } finally { mutex.ReleaseMutex(); }
            }
        }
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
$nativeDebugOutputSource
'@
$nativeDebugHandshakeScript
if (${'$'}ResumeActivation) {
    ${'$'}debugConfiguration = ${'$'}PSCommandPath + '.debug.json'
    try {
        if (Test-Path -LiteralPath ${'$'}debugConfiguration) {
            ${'$'}debug = Get-Content -LiteralPath ${'$'}debugConfiguration -Raw -Encoding UTF8 | ConvertFrom-Json
            if (-not [string]::IsNullOrWhiteSpace([string]${'$'}debug.output)) {
                [KotlinWinRT.UWP.DebugOutput]::Capture(${'$'}p, ${'$'}tid, [string]${'$'}debug.output)
                exit 0
            }
            if (-not [string]::IsNullOrWhiteSpace([string]${'$'}debug.directory)) {
                Wait-NativeDebugger ${'$'}p ${'$'}tid ([string]${'$'}debug.directory)
            }
        }
        [KotlinWinRT.UWP.Launcher]::ResumeActivation(${'$'}p, ${'$'}tid)
    } catch {
        # This activation is still suspended and belongs to the startup helper.
        Stop-Process -Id ${'$'}p -ErrorAction SilentlyContinue
        throw
    }
    exit 0
}
if (${'$'}WatchPackage) {
    try {
        ${'$'}watched = Get-Process -Id ${'$'}WatchProcess -ErrorAction SilentlyContinue
        if (${'$'}watched -and ${'$'}watched.StartTime.ToUniversalTime().Ticks -eq ${'$'}WatchStart) {
            ${'$'}null = ${'$'}watched.Handle
            ${'$'}watched.WaitForExit()
        }
    } finally {
        try {
            [KotlinWinRT.UWP.Launcher]::DevelopmentEnvironment(${'$'}WatchPackage, ${'$'}OwnerFile, ${'$'}Owner, ${'$'}null)
        } finally {
            try {
                ${'$'}finishedDirectory = Join-Path (Split-Path -Parent ${'$'}OwnerFile) ${'$'}Owner
                ${'$'}finishedSession = Join-Path ${'$'}finishedDirectory ("${'$'}WatchProcess-0.session")
                if (Test-Path -LiteralPath ${'$'}finishedSession) { [IO.File]::Delete(${'$'}finishedSession) }
                if (Test-Path -LiteralPath ${'$'}finishedDirectory) { [IO.Directory]::Delete(${'$'}finishedDirectory, ${'$'}false) }
            } finally {
                [IO.File]::Delete(${'$'}PSCommandPath + '.debug.json')
                [IO.File]::Delete(${'$'}PSCommandPath)
            }
        }
    }
    exit 0
}
${'$'}document = New-Object System.Xml.XmlDocument
${'$'}document.Load(${'$'}Manifest)
${'$'}identity = ${'$'}document.Package.Identity
${'$'}application = @(${'$'}document.Package.Applications.Application)[0]
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
        [KotlinWinRT.UWP.Launcher]::Terminate(${'$'}previousPackage.PackageFullName)
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
${'$'}development = -not [string]::IsNullOrWhiteSpace([string]${'$'}configurationData.developmentSessionDirectory)
${'$'}debugging = ${'$'}development -or ${'$'}configurationData.debugOutput -or -not [string]::IsNullOrWhiteSpace([string]${'$'}configurationData.nativeDebugDirectory)
${'$'}watcherStarted = ${'$'}false
if (${'$'}debugging) {
    ${'$'}owner = [Guid]::NewGuid().ToString('N')
    ${'$'}localState = Join-Path ${'$'}env:LOCALAPPDATA ('Packages\' + ${'$'}package.PackageFamilyName + '\LocalState\KotlinWinRT')
    ${'$'}sessionDirectory = Join-Path ${'$'}localState ${'$'}owner
    [IO.Directory]::CreateDirectory(${'$'}sessionDirectory) | Out-Null
    ${'$'}ownerFile = Join-Path ${'$'}localState 'debug-owner'
    # EnableDebugging limits the startup command line to MAX_PATH. Keep the
    # per-activation helper independent of arbitrarily deep project/build paths.
    ${'$'}sessionScript = Join-Path ([IO.Path]::GetTempPath()) ('kwrt-' + ${'$'}owner + '.ps1')
    [IO.File]::Copy(${'$'}PSCommandPath, ${'$'}sessionScript)
    if (${'$'}configurationData.debugOutput -or -not [string]::IsNullOrWhiteSpace([string]${'$'}configurationData.nativeDebugDirectory)) {
        [IO.File]::WriteAllText(${'$'}sessionScript + '.debug.json',
            (@{ directory = [string]${'$'}configurationData.nativeDebugDirectory; output = [string]${'$'}configurationData.debugOutputFile } | ConvertTo-Json -Compress), [Text.UTF8Encoding]::new(${'$'}false))
    }
    ${'$'}environment = @()
    if (${'$'}development) { ${'$'}environment = @(('KOTLIN_WINRT_HOT_RELOAD_DIRECTORY=' + ${'$'}sessionDirectory), 'KOTLIN_WINRT_DEVELOPMENT_TRANSPORT=pipe') }
    if (${'$'}configurationData.designPreview) { ${'$'}environment += 'KOTLIN_WINRT_XAML_PREVIEW=1' }
}
try {
if (${'$'}debugging) {
    # Windows requires a startup debugger when supplying an environment block.
    # Following Microsoft's Store profiler bootstrap, the helper resumes the
    # supplied activation thread, handing off to LLDB or capturing output when requested.
    ${'$'}resumer = '"' + (Join-Path ${'$'}PSHOME 'powershell.exe') + '" -NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File "' + ${'$'}sessionScript + '" -ResumeActivation'
    if (${'$'}resumer.Length -ge 260) { throw 'The Windows startup debugger command exceeds MAX_PATH. Configure a shorter TEMP directory.' }
    [KotlinWinRT.UWP.Launcher]::DevelopmentEnvironment(${'$'}package.PackageFullName, ${'$'}ownerFile, ${'$'}owner, ${'$'}environment, ${'$'}resumer)
}
${'$'}applicationProcessId = [KotlinWinRT.UWP.Launcher]::Activate(${'$'}applicationId, [string]${'$'}configurationData.applicationArguments)
Write-Output "Activated ${'$'}applicationId (PID ${'$'}applicationProcessId)"
if (${'$'}debugging) {
    ${'$'}applicationProcess = Get-Process -Id ${'$'}applicationProcessId -ErrorAction Stop
    # A separate hidden watcher restores package lifecycle even when Gradle is cancelled.
    ${'$'}watchArguments = @(${'$'}sessionScript, ${'$'}package.PackageFullName, [string]${'$'}applicationProcessId,
        [string]${'$'}applicationProcess.StartTime.ToUniversalTime().Ticks, ${'$'}ownerFile, ${'$'}owner) |
        ForEach-Object { "'" + ${'$'}_.Replace("'", "''") + "'" }
    ${'$'}watchCommand = '& ' + ${'$'}watchArguments[0] + ' -WatchPackage ' + ${'$'}watchArguments[1] +
        ' -WatchProcess ' + ${'$'}watchArguments[2] + ' -WatchStart ' + ${'$'}watchArguments[3] +
        ' -OwnerFile ' + ${'$'}watchArguments[4] + ' -Owner ' + ${'$'}watchArguments[5]
    ${'$'}encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes(${'$'}watchCommand))
    Start-Process -FilePath (Join-Path ${'$'}PSHOME 'powershell.exe') -WindowStyle Hidden -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', ${'$'}encoded) | Out-Null
    ${'$'}watcherStarted = ${'$'}true
}
if (${'$'}development) {
    ${'$'}sourceSession = Join-Path ${'$'}sessionDirectory ("${'$'}applicationProcessId-0.session")
    ${'$'}deadline = [DateTime]::UtcNow.AddSeconds(30)
    while (-not (Test-Path -LiteralPath ${'$'}sourceSession)) {
        if (${'$'}applicationProcess.HasExited) { throw 'UWP application exited before publishing its development session.' }
        if ([DateTime]::UtcNow -gt ${'$'}deadline) { throw 'UWP application did not publish its development session within 30 seconds.' }
        Start-Sleep -Milliseconds 100
    }
    if ((Get-Item -LiteralPath ${'$'}sourceSession).Length -gt 16384) { throw 'Invalid UWP development session size.' }
    ${'$'}destination = [IO.Path]::GetFullPath([string]${'$'}configurationData.developmentSessionDirectory)
    [IO.Directory]::CreateDirectory(${'$'}destination) | Out-Null
    ${'$'}temporary = Join-Path ${'$'}destination (${'$'}owner + '.tmp')
    [IO.File]::Copy(${'$'}sourceSession, ${'$'}temporary)
    [IO.File]::Move(${'$'}temporary, (Join-Path ${'$'}destination ([IO.Path]::GetFileName(${'$'}sourceSession))))
}
if (${'$'}configurationData.debugOutput) {
    Write-Output ('Native debug output: ' + [string]${'$'}configurationData.debugOutputFile)
    if (-not ${'$'}Detach) {
        ${'$'}stream = [IO.File]::Open([string]${'$'}configurationData.debugOutputFile, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
        ${'$'}reader = [IO.StreamReader]::new(${'$'}stream, [Text.Encoding]::UTF8)
        try {
            while (-not ${'$'}applicationProcess.HasExited) {
                ${'$'}text = ${'$'}reader.ReadToEnd()
                if (${'$'}text.Length -gt 0) { Write-Host -NoNewline ${'$'}text }
                Start-Sleep -Milliseconds 100
            }
            Start-Sleep -Milliseconds 100
            ${'$'}text = ${'$'}reader.ReadToEnd()
            if (${'$'}text.Length -gt 0) { Write-Host -NoNewline ${'$'}text }
        } finally { ${'$'}reader.Dispose(); ${'$'}stream.Dispose() }
    }
}
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
} catch {
    if (${'$'}debugging -and ${'$'}applicationProcess -and -not ${'$'}applicationProcess.HasExited) {
        Stop-Process -InputObject ${'$'}applicationProcess -ErrorAction SilentlyContinue
    }
    throw
} finally {
    if (${'$'}debugging -and -not ${'$'}watcherStarted) {
        [KotlinWinRT.UWP.Launcher]::DevelopmentEnvironment(${'$'}package.PackageFullName, ${'$'}ownerFile, ${'$'}owner, ${'$'}null)
        [IO.File]::Delete(${'$'}sessionScript + '.debug.json')
        [IO.File]::Delete(${'$'}sessionScript)
    }
}
""".trimIndent()
