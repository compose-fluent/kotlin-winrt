# UWP XAML applications

This guide describes the UWP implementation in the source tree. The Native Debug and Release
sample has passed real AppContainer activation, XAML resource loading, compiled
binding, and button event validation. JVM execution remains blocked as described
below; configuration and build support alone do not establish runtime support. For the current
sample and its verification status, see [uwp-app](../winrt-samples/uwp-app/README.md).
The published WinUI quick start is a separate application setup.

The bundled OpenJDK 25.0.4 image currently fails during JVM application startup in
AppContainer. `java.security.Security` loads `conf/security/java.security` through
`WindowsPath.toRealPath()`, whose parent-directory traversal throws
`AccessDeniedException` even though the configuration file is included in the
package. A successful JVM image build or package activation is therefore **not**
JVM UWP runtime support. This requires an AppContainer-compatible JDK filesystem
implementation and subsequent end-to-end validation; changing the application to
full trust or bypassing Java security configuration does not resolve that requirement.

## Select the application model and XAML framework

WinRT is the Windows Runtime API and its projection technology. AppX/MSIX is a
package format: a packaged desktop application does not become a UWP application.
WinUI 3 belongs to Windows App SDK and uses `Microsoft.UI.Xaml`. UWP uses the
Windows SDK's `Windows.UI.Xaml` and runs with AppContainer trust.

Configure a UWP application using the toolkit from the same checkout:

```kotlin
import io.github.composefluent.windows.toolkit.gradle.WindowsApplicationModel
import io.github.composefluent.windows.toolkit.gradle.WindowsAppSdkDeployment
import io.github.composefluent.windows.toolkit.gradle.WindowsPackageType

kotlin {
    jvmToolchain(25)
    jvm()
    mingwX64 { binaries.executable() }
}

windows {
    application {
        applicationModel = WindowsApplicationModel.UWP
        packageType = WindowsPackageType.Packaged
        windowsAppSdkDeployment = WindowsAppSdkDeployment.None
        mainClass = "sample.uwp.MainKt"
        minWindowsVersion = "10.0.19041.0"
    }
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false)
        type("Windows.UI.Xaml.Application")
        type("Windows.UI.Xaml.Window")
        type("Windows.UI.Xaml.Controls.Page")
        type("Windows.UI.Xaml.Controls.Button")
        type("Windows.UI.Xaml.Controls.TextBlock")
    }
}
```

`applicationModel` defaults to `Desktop`. Selecting `UWP` makes
`windows.xaml.framework` default to `XamlFramework.UWP`. UWP requires packaged
identity, deployment `None`, and that UWP framework selection. `None` means that
Windows App SDK is not deployed; it does not exclude a UWP component's AppX
framework dependencies. The desktop Windows App SDK's four explicit deployment
formats do not apply to UWP.

XAML libraries and projection modules have no application model to infer from.
Select the framework explicitly in each UWP module:

```kotlin
import io.github.composefluent.winrt.runtime.XamlFramework

windows {
    xaml { framework = XamlFramework.UWP }
}
```

Keep XAML, Kotlin and AppX resources in the existing shared `src/winuiMain` source
set. Its name is retained for source-layout compatibility; it does not select a
framework. Use `windows.ui.xaml` projections for the system controls and the
Windows SDK's activation arguments. UWP's application window is obtained through
`Window.current`; a WinUI 3 `Window()`/`AppWindow` startup is not interchangeable.
The generated hosts initialize `XamlRuntime` before user code. A custom host must
select the same framework before creating any XAML projections.

System UWP loads the application's XAML during framework startup. Following the
Windows SDK's C++/WinRT Application generator, Kotlin does not call `LoadComponent`
for an Application. Packaging places its compiled resource at `App.xbf` in the
package root even when the Kotlin/XAML source has a different name or directory.
Source and IDE paths remain unchanged. Ordinary pages still use `LoadComponent`.
Only one XAML Application is allowed, and conflicting root resources are rejected.

## Supply the UWP compiler

Build on Windows x64 with the [normal JDK/C++ prerequisites](../README.md#requirements)
and a Windows SDK that includes XAML compiler tools. UWP defaults to the published
[kotlin-xamlc 0.1.0-preview.7](https://github.com/compose-fluent/microsoft-ui-xaml/releases/tag/kotlin-xamlc-v0.1.0-preview.7)
UWP archive, downloaded automatically and verified against SHA-256
`c771ea8a7875500ed293e8da7d9de0b27d87695d9b4a8999143d50a2e63696d2`.

The UWP backend is already part of `kotlin-xamlc`, not a separate compiler project.
Its `Kotlin XamlCompiler` CI builds and tests both `winui` and `uwp`, and the same
release workflow publishes both framework archives. Each framework uses its own
archive and checksum.

For compiler development, optionally override with an unpacked **UWP** package:

```powershell
$uwpCompiler = 'C:/tools/kotlin-xamlc-<version>-uwp-win-x64'
.\gradlew.bat :winrt-samples:uwp-app:compileKotlinJvm "-PkotlinWinRT.xaml.compilerDirectory=$uwpCompiler"
```

Replace the path with an existing package directory containing `XamlCompiler.exe`
and `kotlin-xamlc.json`, including its declared files and checksums. A bare MSBuild
output directory is insufficient. The same-source compiler backend is built with
`KotlinXamlFramework=uwp`; its package must declare `uwp` framework compatibility
and the selected Windows SDK version. The WinUI compiler package is not a UWP
backend override. Runtime/compiler/projection artifacts must come from the same
kotlin-winrt build.

The DSL equivalent is
`windows { xaml { compilerDirectory = layout.projectDirectory.dir("C:/tools/<uwp-package>") } }`.
A local package needs no `compilerVersion` or archive checksum setting. If using a
published custom UWP distribution instead, set both `compilerVersion` and
`archiveSha256` (or `kotlinWinRT.xaml.version` and `kotlinWinRT.xaml.sha256`). The
default URL uses `kotlin-xamlc-<version>-uwp-win-x64.zip`; `archiveUrl` can select a
different HTTPS endpoint. Use that UWP archive's checksum, not the WinUI checksum.

UWP XBF generation uses the selected **Windows SDK's** GenXbf. Discovery checks
`bin/<sdk-version>/XamlCompiler/x64/GenXbf.dll` and the older
`bin/<sdk-version>/x64/GenXbf.dll` layout. An override through
`kotlinWinRT.xaml.genXbfDirectory` or `windows.xaml.genXbfDirectory` names the
directory **containing `x64`**, not the DLL or the `x64` directory. No machine-local
SDK path needs to be committed to the project.

## Build and activate each target

The sample declares target names `jvm` and `mingwX64`. From the repository root,
compile/link each target with the compiler path above:

```powershell
.\gradlew.bat :winrt-samples:uwp-app:compileKotlinJvm "-PkotlinWinRT.xaml.compilerDirectory=$uwpCompiler"
.\gradlew.bat :winrt-samples:uwp-app:linkDebugExecutableMingwX64 "-PkotlinWinRT.xaml.compilerDirectory=$uwpCompiler"
```

Enable Windows Developer Mode, then use package activation for runtime validation.
Run one target at a time because the variants share a development package identity:

```powershell
.\gradlew.bat :winrt-samples:uwp-app:runWinAppPackageJvmMain "-PkotlinWinRT.xaml.compilerDirectory=$uwpCompiler" --detach
.\gradlew.bat :winrt-samples:uwp-app:runWinAppPackageMingwX64MainDebugExecutable "-PkotlinWinRT.xaml.compilerDirectory=$uwpCompiler" --detach
```

The UWP runner registers the `.dev` layout and activates it through Windows'
application activation manager. It installs matching missing framework dependencies
from the resolved package assets. Redeployment terminates only the matching
development package, preserves its application data, and refreshes registration so
that switching target directories also updates manifest extensions. A registration
failure attempts to restore the previous manifest. An installed non-development
package is not removed by this flow.

`--args='--flag value'` supplies the activation command line, `--no-launch` registers
without activation, and `--detach` returns after activation. Otherwise the runner
waits for the acquired application process and propagates its exit code. A direct
EXE/Java launch does not exercise UWP package activation. To create MSIX artifacts,
use `packageWinAppJvmMain` or `packageWinAppMingwX64MainReleaseExecutable` on the same
module; signing and installation retain the [normal packaging settings](USAGE.md).

JVM UWP applications require `WinAppJvmRuntimeMode.Bundled`, which is the default.
The plugin builds a runtime image, or accepts `application.jvmRuntimeImage` for a
matching prebuilt image. The deployed host loads the package's JVM and does not use
the machine's `JAVA_HOME`. Manifest staging merges the package root and existing
JVM DLL directories into `uap6:LoaderSearchPathOverride`, retaining user paths and
enforcing the five-path limit. Native packages do not carry that JVM image.

Keep the source manifest and assets under `src/winuiMain/appxResources`. Its
`Application.EntryPoint` must name the application class. Staging adds
`uap10:RuntimeBehavior="windowsApp"` and `uap10:TrustLevel="appContainer"`; it rejects
`Windows.FullTrustApplication` and `runFullTrust`. Declare `codeGeneration` for the
runtime's dynamic ABI callbacks. The sample manifest provides the concrete layout.
Application file access and other operations remain subject to AppContainer rules;
use package resources and UWP application-data APIs for the sample's assets/logs.

## Background tasks

In-process tasks omit `BackgroundTaskBuilder.taskEntryPoint`; Windows delivers
them to the projected `Application.onBackgroundActivated` override. Take a
deferral from the task instance before asynchronous work and complete it when the
work ends. The Gallery demonstrates `ApplicationTrigger`, `TimeTrigger`, and
`SystemTrigger`, including registration completion and callback-result checks.

For an out-of-process task, use an authored WinRT class exposing `IBackgroundTask`
and set `taskEntryPoint` to its runtime class name. The manifest must declare the
matching `windows.backgroundTasks` extension and task type. The toolkit merges
the authored host's class-to-DLL mapping into the package-level
`windows.activatableClass.inProcessServer` extension; an EXE side-by-side manifest
alone does not register the component for UWP background activation.

See the [Native background component](../winrt-samples/uwp-background-task/README.md)
and [Gallery validation instructions](../winui-gallery/README.md). Windows debug
activation has verified both the in-process system-event callback and the Native
DLL in `backgroundTaskHost.exe`, including deferral completion and a matching
application-data receipt. Debug activation does not validate natural scheduling.
Separately, the Gallery's one-shot timer has naturally cold-started the application
and entered `onBackgroundActivated` after the foreground process exited; Windows
consumed the one-shot registration. Natural time-zone events remain untested.
The JVM AppContainer limitation above still applies to JVM-hosted components.

## WinUI 2 and remaining validation

WinUI 2 is a UWP controls library distributed as the `Microsoft.UI.Xaml` NuGet
package. Its `Microsoft.UI.Xaml` controls derive from `Windows.UI.Xaml`; that
namespace does not mean WinUI 3 or Windows App SDK. Keep `XamlFramework.UWP` when
adding WinUI 2 metadata and runtime assets, and project its control types alongside
the Windows SDK UWP types. Do not substitute the Windows App SDK projection family.
Framework package dependencies and component metadata providers are selected from
the resolved package metadata, independently of Windows App SDK deployment.

The sample pins `Microsoft.UI.Xaml` 2.8.7, merges `XamlControlsResources`, and
places a WinUI 2 `NumberBox` beside a system `Button`. Native validation includes
XBF generation, the staged `Microsoft.UI.Xaml.2.8` framework dependency, control
rendering and spin-button interaction in Debug, and the typed `ValueChanged`
callback entering Kotlin in Release. Application-data logs remain intact across
Debug/Release redeployment. The local UWP compiler package used for these checks
is `0.1.0-uwp.3`; earlier development packages had an incompatible private XBF COM
interface layout that built-in system controls alone did not expose.

JVM WinUI 2 execution still requires resolving the JDK startup limitation above.
Installing framework dependencies on a clean machine also remains an integration
check; the current machine already had a compatible WinUI 2 framework installed.

Native UWP development launches support the IDE's project-code XAML preview and
live property Hot Reload. Synchronize Gradle, select the Native packaged launch,
and enable project code for preview. The isolated `.preview` package starts the
generated Application instead of the user's main/Application constructor; custom
controls still execute their compiled constructors. UWP uses its activated
CoreWindow. The independent SDK-only designer remains a desktop WinUI host.

The launcher supplies development settings through `IPackageDebugSettings`, using
the Windows startup-thread resume contract, and transfers the session descriptor
from the package's LocalState into the IDE session directory. Authenticated named
pipes use the actual AppContainer namespace and an ACL restricted to the user and
that package SID. No loopback exemption is needed. A separate process restores
normal package lifecycle and removes its helper/session when the application exits,
including after the Gradle launcher has detached. Concurrent preview and live
packages retain independent lifecycle ownership.

Windows acceptance covers Native UWP activation, light/dark previews, unsaved
markup, actual templates/pixels/property readback, live text/button updates,
transaction rollback and process-exit cleanup. IDE runtime tests accept
`-Pwinrt.ide.uwpPreviewSession=<directory>` and
`-Pwinrt.ide.uwpHotReloadSession=<directory>` for these real application sessions.
Native IDE **Debug** builds and activates the selected Native Debug executable,
attaches LLDB before application code and supports source breakpoints, stepping,
thread stacks and Kotlin object fields. Configure LLDB under Kotlin WinRT settings;
the Gradle model supplies the matching Kotlin/Native compiler formatter.

`runWinAppPackage... --debug-output` captures Windows `OutputDebugString` messages
(including Unicode), first/second-chance exception notifications and process exit.
Application exception handlers still receive their exceptions. Output streams to
the Gradle console and is retained at `build/tmp/<run-task>/native-debug-output.log`.
With `--detach`, capture continues in the activation helper and the task prints the
log path. This captures native debugger output, not ordinary stdout/stderr.
Windows permits one debugger per process, so `--debug-output` and IDE LLDB Debug
are separate launch modes. The helper and package debug lease end with the app.
See the [XAML compiler guide](../windows-toolkit-gradle-plugin/XAML.md) and
[runtime ABI contract](../winrt-runtime/XAML_CONTRACT.md) for shared contracts.
