param(
    [Parameter(Mandatory)] [ValidateSet('Protocol', 'File')] [string] $Kind,
    [string] $Value
)
$ErrorActionPreference = 'Stop'
$packages = @(Get-AppxPackage -Name 'ComposeFluent.KotlinWinUI2Gallery.dev' |
    Where-Object { $_.Name -eq 'ComposeFluent.KotlinWinUI2Gallery.dev' -and $_.IsDevelopmentMode })
if ($packages.Count -ne 1) { throw 'Install exactly one UWP Gallery development package before testing activation.' }
$appId = "$($packages[0].PackageFamilyName)!App"
if (!$Value) {
    if ($Kind -eq 'Protocol') { $Value = 'kotlin-winrt-gallery://UwpActivation' }
    else {
        $Value = Join-Path $env:TEMP 'kotlin-gallery-activation.kwgallery'
        'Kotlin/WinRT file activation validation' | Set-Content -LiteralPath $Value -Encoding utf8
    }
}
if ($Kind -eq 'File') { $Value = (Resolve-Path -LiteralPath $Value).Path }

# SDK IApplicationActivationManager and Shell item APIs. This invokes the real
# activation broker; it does not call Gallery handlers or automate the UI.
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
namespace KotlinGallery.ActivationTest {
    [ComImport, Guid("2E941141-7F97-4756-BA1D-9DECDE894A3D"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IApplicationActivationManager {
        [PreserveSig] int ActivateApplication([MarshalAs(UnmanagedType.LPWStr)] string id,
            [MarshalAs(UnmanagedType.LPWStr)] string args, uint options, out uint pid);
        [PreserveSig] int ActivateForFile([MarshalAs(UnmanagedType.LPWStr)] string id,
            IntPtr items, [MarshalAs(UnmanagedType.LPWStr)] string verb, out uint pid);
        [PreserveSig] int ActivateForProtocol([MarshalAs(UnmanagedType.LPWStr)] string id,
            IntPtr items, out uint pid);
    }
    [ComImport, Guid("45BA127D-10A8-46EA-8AB7-56EA9078943C")]
    class ApplicationActivationManager { }
    public static class Broker {
        [DllImport("shell32.dll", CharSet=CharSet.Unicode, PreserveSig=true)]
        static extern int SHCreateItemFromParsingName(string path, IntPtr bindContext, ref Guid iid, out IntPtr item);
        [DllImport("shell32.dll", PreserveSig=true)]
        static extern int SHCreateShellItemArrayFromShellItem(IntPtr item, ref Guid iid, out IntPtr items);
        public static uint Activate(string id, string kind, string value) {
            var itemId = new Guid("43826D1E-E718-42EE-BC55-A1E261C37BFE");
            var arrayId = new Guid("B63EA76D-1F85-456F-A19C-48159EFA858B");
            IntPtr item = IntPtr.Zero, items = IntPtr.Zero;
            var manager = (IApplicationActivationManager)new ApplicationActivationManager();
            try {
                Marshal.ThrowExceptionForHR(SHCreateItemFromParsingName(value, IntPtr.Zero, ref itemId, out item));
                Marshal.ThrowExceptionForHR(SHCreateShellItemArrayFromShellItem(item, ref arrayId, out items));
                uint pid;
                int result = kind == "Protocol" ? manager.ActivateForProtocol(id, items, out pid)
                    : manager.ActivateForFile(id, items, "open", out pid);
                if (result < 0) throw new COMException(kind + " activation failed (0x" + result.ToString("X8") + ").", result);
                return pid;
            } finally {
                if (items != IntPtr.Zero) Marshal.Release(items);
                if (item != IntPtr.Zero) Marshal.Release(item);
                Marshal.FinalReleaseComObject(manager);
            }
        }
    }
}
'@
$galleryProcessId = [KotlinGallery.ActivationTest.Broker]::Activate($appId, $Kind, $Value)
Write-Output "$Kind activation accepted for $appId (PID $galleryProcessId). Verify its callback in LocalState/gallery.log."
