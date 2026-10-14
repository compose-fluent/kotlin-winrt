param(
    [ValidateSet('KotlinGallery.TimeTrigger', 'KotlinGallery.SystemTrigger', 'KotlinGallery.Component')]
    [string] $TaskName,
    [switch] $Activate
)
$ErrorActionPreference = 'Stop'
$packages = @(Get-AppxPackage -Name 'ComposeFluent.KotlinWinUI2Gallery.dev' |
    Where-Object { $_.Name -eq 'ComposeFluent.KotlinWinUI2Gallery.dev' -and $_.IsDevelopmentMode })
if ($packages.Count -ne 1) { throw 'Install exactly one UWP Gallery development package.' }
if ($Activate -and !$TaskName) { throw 'Specify the exact registered Gallery task to activate.' }

# Windows SDK shobjidl_core.h IPackageDebugSettings. Debug activation verifies
# broker dispatch and the callback; it is not proof of natural timer scheduling.
Add-Type -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Runtime.InteropServices;
namespace KotlinGallery.BackgroundTest {
    [ComImport, Guid("F27C3930-8029-4AD1-94E3-3DBA417810C1"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IPackageDebugSettings {
        [PreserveSig] int EnableDebugging([MarshalAs(UnmanagedType.LPWStr)] string package, IntPtr debugger, IntPtr environment);
        [PreserveSig] int DisableDebugging([MarshalAs(UnmanagedType.LPWStr)] string package);
        [PreserveSig] int Suspend([MarshalAs(UnmanagedType.LPWStr)] string package);
        [PreserveSig] int Resume([MarshalAs(UnmanagedType.LPWStr)] string package);
        [PreserveSig] int TerminateAllProcesses([MarshalAs(UnmanagedType.LPWStr)] string package);
        [PreserveSig] int SetTargetSessionId(uint session);
        [PreserveSig] int EnumerateBackgroundTasks([MarshalAs(UnmanagedType.LPWStr)] string package,
            out uint count, out IntPtr ids, out IntPtr names);
        [PreserveSig] int ActivateBackgroundTask(ref Guid taskId);
    }
    [ComImport, Guid("B1AEC16F-2383-4852-B0E9-8F0B1DC66B4D")]
    class PackageDebugSettings { }
    public static class Broker {
        public static string[] Run(string package, string selected, bool activate) {
            var settings = (IPackageDebugSettings)new PackageDebugSettings();
            uint count = 0;
            IntPtr ids = IntPtr.Zero, names = IntPtr.Zero;
            var result = new List<string>();
            bool found = false;
            try {
                Marshal.ThrowExceptionForHR(settings.EnumerateBackgroundTasks(package, out count, out ids, out names));
                for (int index = 0; index < count; index++) {
                    string name = Marshal.PtrToStringUni(Marshal.ReadIntPtr(names, index * IntPtr.Size));
                    var id = (Guid)Marshal.PtrToStructure(IntPtr.Add(ids, index * 16), typeof(Guid));
                    if (!name.StartsWith("KotlinGallery.", StringComparison.Ordinal)) continue;
                    result.Add(name + " " + id);
                    if (activate && name == selected) {
                        Marshal.ThrowExceptionForHR(settings.ActivateBackgroundTask(ref id));
                        result.Add("Debug activation accepted: " + name + ". Verify callback and completion in gallery.log.");
                        found = true;
                    }
                }
                if (activate && !found) throw new InvalidOperationException("Register this task in Gallery first: " + selected);
                return result.ToArray();
            } finally {
                if (names != IntPtr.Zero) {
                    for (int index = 0; index < count; index++) Marshal.FreeCoTaskMem(Marshal.ReadIntPtr(names, index * IntPtr.Size));
                    Marshal.FreeCoTaskMem(names);
                }
                if (ids != IntPtr.Zero) Marshal.FreeCoTaskMem(ids);
                Marshal.FinalReleaseComObject(settings);
            }
        }
    }
}
'@
[KotlinGallery.BackgroundTest.Broker]::Run($packages[0].PackageFullName, $TaskName, $Activate.IsPresent)
