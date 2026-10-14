# UWP background component

This Kotlin/Native shared library implements the same boundary as
`.cswinrt/src/Samples/BgTaskComponent/BgTaskComponent/ToastBgTask.cs`: an authored
WinRT runtime class exposes `Windows.ApplicationModel.Background.IBackgroundTask`.
Windows loads its generated activation factory into `backgroundTaskHost.exe` and
calls `Run`. The task completes a deferral and writes a receipt to the owning
package's application data. The receipt lets the Gallery distinguish a successful
broker request from an executed Kotlin callback.

The UWP Gallery declares the `windows.backgroundTasks` extension and consumes the
component through its Native packaging identity dependency. The toolkit stages the DLL,
WinMD and generated activatable-class registration. The JVM AppContainer startup
limitation in [UWP support](../../docs/UWP.md) also applies to background components;
this sample currently validates only the Native component.

Local validation has confirmed Windows debug activation, successful task completion,
and the matching receipt written by this DLL. This does not establish JVM support
or natural time-zone event scheduling.

On the Gallery's **UWP activation** page, register the Native background component.
For deterministic callback validation without changing the machine's time zone:

```powershell
.\winui-gallery\tools\Invoke-UwpGalleryBackgroundTask.ps1 `
    -TaskName KotlinGallery.Component -Activate
```

This calls the Windows SDK's `IPackageDebugSettings.ActivateBackgroundTask` for an
existing registration in the Gallery development package. Verify both completion
and the matching callback receipt in `LocalState/gallery.log`. It does not prove
that the natural system event occurred. Use the page's cleanup button to remove
remaining Gallery test registrations.
