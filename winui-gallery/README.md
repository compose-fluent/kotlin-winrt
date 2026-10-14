# Kotlin WinUI Gallery

## Selecting WinUI 2 or WinUI 3

The same module supports two **build-time** variants. The default remains WinUI 3;
pass `-PkotlinWinRT.gallery.winuiVersion=2` for the UWP / WinUI 2.8.7 Gallery.
Gradle selects the Kotlin/XAML sources, SDK dependencies, manifest, and application
model together. `prepareGallerySources` selects conditional source branches before
KSP and XAML compilation. The UWP variant builds into `build/winui2`,
with a separate package identity, so it can coexist with the desktop Gallery.

```powershell
.\gradlew.bat :winui-gallery:runWinAppPackageMingwX64MainReleaseExecutable --detach `
    '-PkotlinWinRT.gallery.winuiVersion=2'
```

For the full Native Debug Gallery, use an 8 GB G1 build heap and release idle
heap while LLVM generates machine code. The default 4 GB Serial GC build heap
can spend most of the link in GC, and retaining Java heap also increases LLVM's
peak memory pressure. Run one full Gallery build at a time:

```powershell
.\gradlew.bat :winui-gallery:runWinAppPackageMingwX64MainDebugExecutable --detach `
    '-PkotlinWinRT.gallery.winuiVersion=2' --max-workers=1 `
    '-Dorg.gradle.jvmargs=-Xmx8g -XX:+UseG1GC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 -XX:G1PeriodicGCInterval=30000 -Dfile.encoding=UTF-8'
```

These settings retain all pages and Native debug information. LLVM also allocates
memory outside the Java heap, so `-Xmx` is not the build's total memory limit.

The UWP compiler downloads automatically; see [UWP support](../docs/UWP.md). Native is
the runnable UWP target. The JVM source/compilation path is also maintained, but
the OpenJDK AppContainer startup limitation documented there still applies.
Use `--args=NumberBox` to open a particular page, or `--args=--validate-controls`
to construct and lay out all catalog pages after the shell loads. Validation
results and event output are written to the package's `LocalState/gallery.log`.
This smoke path checks activation/layout; it does not replace manual input tests.

The Native UWP Gallery logs launch execution state and background, suspension,
and resume events to the same log. Manual validation covers cold launch,
theme/recent-page persistence across exit and launch, and minimizing until the
OS suspends the app, then restoring the same page and interacting with it.
The manifest registers `kotlin-winrt-gallery:` and `.kwgallery`. The UWP activation
page exercises protocol/file launch and an in-process `ApplicationTrigger` task.
Its task omits `TaskEntryPoint`, so Windows invokes `Application.OnBackgroundActivated`;
the callback takes and completes a deferral and records its execution before the
registration's completion handler removes the test task.

For repeatable protocol/file tests without changing default app associations, use
`tools/Invoke-UwpGalleryActivation.ps1 -Kind Protocol` or `-Kind File`. The script
uses the Windows activation broker for this development package. Verify the
corresponding callback in `gallery.log`, both while running and after closing
the app; a successful broker call alone is not proof that the callback completed.
The same page registers a one-shot 15-minute `TimeTrigger`, an in-process
time-zone `SystemTrigger`, and a Native out-of-process background component.
The component follows CsWinRT's `BgTaskComponent` sample and implements
`IBackgroundTask` in `winrt-samples/uwp-background-task`. Its packaging identity
provides the DLL and activation mapping without linking the component into the UI.
Use `tools/Invoke-UwpGalleryBackgroundTask.ps1 -TaskName KotlinGallery.SystemTrigger -Activate`
or `-TaskName KotlinGallery.Component -Activate` after registering the corresponding
task. This uses the Windows SDK debug activation API; it does not change the
machine's time zone or prove natural trigger scheduling. Check both the completion
event and matching callback receipt in `gallery.log`. Completed tasks unregister
themselves; the cleanup button removes any remaining Gallery test registrations.

Native UWP validation covers protocol and file activation both while running and
after closing the app, including reading the activated file's name. It also covers
the ApplicationTrigger request, background callback, deferral completion and task
completion event. Theme checks cover a saved dark theme on cold startup and
switching back to the current system theme. Multiple-window creation, closing and
reopening are exercised in the Gallery. Toast submission and badge update/clear
calls succeed. Notification-center display and clicking a toast while the app is
running have been verified; banner display and notification cold activation need
separate verification. The system-event and Native component debug activations
also passed completion and callback-receipt checks, including Windows loading the
component in `backgroundTaskHost.exe`.
The one-shot `TimeTrigger` also fired naturally after the foreground process was
terminated: a new Gallery process logged `Application.onBackgroundActivated` for
`KotlinGallery.TimeTrigger`, then suspended. Its registration was consumed without
using the debug activation API. Natural time-zone changes remain untested.

Both variants use the layouts and pages in `src/winuiMain/kotlin`, including the
navigation shell, Home, Settings, page headers, examples and source viewer.
`src/uwpMain/kotlin` contains UWP startup/activation and compatibility resources; target-specific
logging lives in `src/winui2JvmMain` and `src/winui2MingwX64Main`. Existing target
identity declarations and the Gallery's AppX image/icon assets are shared.
The UWP application uses `Windows.UI.Xaml` with `Microsoft.UI.Xaml.Controls`
extensions, following the SDK's UWP activation contract (`Window.Current`).
The WinUI 3 application follows `.cswinrt/src/Samples/WinUIDesktopSample` and
retains its desktop activation/windowing contract.

Multiple windows uses `Windows.UI.WindowManagement.AppWindow` on UWP, with
`ElementCompositionPreview.SetAppWindowContent` to host a XAML page. The closed
handler clears the page's content and releases the retained window reference.
Toast and badge samples use `Windows.UI.Notifications`; toast activation returns
through `Application.OnActivated`. Shared page layouts retain UWP-specific source
examples and documentation links from the catalog's `Uwp` overrides.

Unsupported pages and their dependent desktop helpers are listed with reasons in
`winui2-exclusions.tsv`. KSP builds navigation from the selected pages, so an
excluded page cannot remain as a broken navigation entry. A page with only one
unsupported example keeps its other examples (for example TabView windowing and
SplitMenuFlyoutItem). UWP uses `WinUIEssential.UWP` 1.3.3: SettingsCard and
SettingsExpander retain the shared settings layout, and Segmented supplies the
shell/source tabs where SelectorBar is unavailable. Their package resource
dictionaries are merged into application resources.
The UWP manifest declares `Microsoft.VCLibs.140.00` for the native runtime used
by WinUIEssential; installing the VC desktop redistributable alone does not
satisfy an AppContainer package dependency.
The UWP shell applies WinUI 2 `BackdropMaterial` (Mica on supported Windows 11,
solid fallback on Windows 10), extends into the system title bar and uses
`Window.SetTitleBar` for dragging. Search remains interactive above that region;
caption insets, theme colors and inactive appearance follow the UWP window APIs.
Back and pane-toggle buttons share the title-bar row with the desktop shell.
Source/design tabs use WinUIEssential's `PivotSegmentedStyle`; Home uses
`GalleryUwpTokenStyles.xaml` to match the desktop token buttons, including
their rounded shape, accent selection colors and eight-pixel spacing.
Settings-page switches remove the UWP control's unused minimum width.
The UWP SettingsCard
resource overlay gives About an explicit vertical item template: the 1.3.3
enum-based alignment trigger does not activate in this UWP path.
`GalleryUwpStyles.xaml` ports the newer typography/subtle-button resources needed
by the shared layouts; these are Gallery resources, not additional UWP SDK APIs.

Use `// #if WINUI2`, `// #if WINUI3`, `// #else`, `// #endif` for Kotlin differences,
and the same directives inside XML comments for XAML. Both builds compile the
prepared sources in `build[/winui2]/generated/gallery-sources`; do not edit those
outputs. Type names are resolved against the selected metadata: WinUI 2 extensions
retain `Microsoft.UI.Xaml.Controls`, and platform controls use `Windows.UI.Xaml`.
The Gallery sets `windows.xaml.enabled` explicitly so the XAML compilation tasks
exist before source selection produces its output on a clean checkout.
Explicit UWP namespaces are needed where the two frameworks expose distinct types
with the same short name, such as an `IconSource` used by a system XamlUICommand.
Runtime projection types and ABI identities are not aliased.

The Gallery uses adjacent XAML and Kotlin code-behind files. WinUI 3 preserves all
122 routes, including Home; WinUI 2 selects 110 of those routes and adds the UWP
activation page. Both use the static layouts, resources,
styles and templates from official WinUI Gallery `v2.9.3`, commit
`14a4a1a2b8ddc527dc4a7d5f7e743d7c2bc97db7`.
Windows App SDK remains **2.5.1**.

Application code is shared by `winuiJvm` and `mingwX64` under
`src/winuiMain/kotlin`. Runtime, metadata, projection and authoring behavior
belongs to the corresponding Kotlin/WinRT modules and follows `.cswinrt/src`.
The Gallery contains application behavior and XAML controls, without its own
ABI bridge or markup interpreter.

## XAML compilation

The Windows toolkit discovers `.xaml` beside `.kt`, verifies `x:Class`, and
passes the projected SDK names and application symbols to the custom compiler.
There is no `@XamlPage`, generated superclass, Kotlin compiler fork, or runtime
search for handlers. FIR adds members and interfaces; IR supplies their bodies.
`x:Name` becomes a typed property and events can call private Kotlin methods.
The connector uses the existing page's authored COM identity.

The build graph is declaration analysis → isolated semantic compilation →
XamlCompiler/XBF generation → final Kotlin compilation → PRI/package staging.
The semantic JAR or KLIB is not an application dependency. JVM and Native final
outputs have separate directories.

The toolkit pins the CI Release
[`kotlin-xamlc-v0.1.0-preview.6`](https://github.com/compose-fluent/microsoft-ui-xaml/releases/tag/kotlin-xamlc-v0.1.0-preview.6),
protocol 3, from fork commit `089179eeecac14b587981afe05cf8686c9da0923`.
Its archive SHA-256 is
`7718088e70e1d4e95446e0b1e6891fcce09ab70c82ba3ae6027d6aaa43fbc628`.
Download and package checks run in Gradle, not the Kotlin compiler. GenXbf comes
from the selected Windows App SDK package. Building the application does not
require the XamlCompiler checkout or a local tool override.

The compiler supports named elements, ordinary and compiled-binding events,
compiled bindings, template scopes, collection notifications, converters,
phased bindings, deferred elements, application resources and custom Kotlin
XAML types. `x:Properties` adds typed Kotlin properties, literal or complex
defaults, read-only declarations and property change events. The main window
uses its XAML-declared `GalleryTitle` in Kotlin and a OneWay compiled binding.
Unsupported forms produce build errors.

The `models` library supplies the plain Kotlin Recipe model and its inferred XAML
schema. The `resources` library supplies the shared grid and text dictionaries
as XBF through AppX resource variants. Neither library generates SDK projections
or requires its model classes to be Windows Runtime components. Existing
dictionary `ms-appx:///` paths remain unchanged.

## Initialization and navigation

The plugin calls `initializeComponent()` after complete construction. A page can
override it, call `super.initializeComponent()`, then use generated controls:

```kotlin
@GalleryPage(route = "Button", title = "Button", group = "BasicInput", order = 0)
internal class ButtonPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
    }

    private fun onStandardClick(sender: Any?, args: RoutedEventArgs) {
        textOutput.text = "You clicked: Standard XAML button"
    }
}
```

`@GalleryPage` registers a zero-argument `UIElement` subclass directly. KSP emits
typed constructor calls, navigation metadata and source documents. The existing
function registration remains compatible. The annotation does not associate
XAML with a Kotlin class. Other XAML classes can have constructor arguments;
only types activated by markup must have an accessible default constructor.

`GalleryApplication`, `MainWindow`, Home, Settings, `ItemPage`, `PageHeader` and
`ControlExample` have their own adjacent XAML. Navigation, preferences, search,
favorites, recents, window retention, deep links and notifications stay in
Kotlin. See [XAML_MIGRATION.md](XAML_MIGRATION.md) for the route inventory and
actual acceptance state.

Launch arguments and protocol activation use the same route resolver as
in-app navigation. It accepts generated page and group IDs or titles, plus
the shell routes `Home`, `All`, `Settings` and `Search:<query>`.

## Independent example sources

The 355 independent example documents live in `SampleDefinitions/<Route>/*.txt`.
Each example resolves its own document.
`ControlExample` shows the relevant XAML and Kotlin tabs, applies live code
substitutions, and copies the displayed source. Pure markup examples have one
XAML tab. A complete page file is not repeated for every example.
KSP and the existing compiler highlighting extension produce source text and
highlight ranges; `SampleCodePresenter` renders selectable native text with
light, dark and high contrast palettes.

## Windows build and run

Use JDK 25 and the Windows SDK/build tools already required by the repository.
The normal Gradle entry points resolve and compile XAML automatically:

```powershell
.\gradlew.bat :winui-gallery:runWinAppPackageWinuiJvmMain --args=Home
.\gradlew.bat :winui-gallery:runWinAppPackageWinuiJvmMain --args=Button
.\gradlew.bat :winui-gallery:runWinAppPackageMingwX64MainDebugExecutable --args=Home
.\gradlew.bat :winui-gallery:stageWinAppDevelopmentPackageMingwX64MainDebugExecutable
.\gradlew.bat :winui-gallery:signWinAppPackageWinuiJvmMain
.\gradlew.bat :winui-gallery:signWinAppPackageMingwX64MainReleaseExecutable
```

Use `--offline` once dependencies and the verified compiler package are cached.
The existing snapshot workflow builds JVM and Native packages without cloning
the XamlCompiler fork. Signing uses the existing certificate configuration.
For the full Gallery Release, run JVM and Native packaging in separate Gradle
invocations with `--no-daemon`. The workflow uses an 8 GB JVM build heap, then a separate Native compiler process
with 12 GB and a 2 GB Gradle heap. Keep `--max-workers=1` when building the full
Gallery and avoid running JVM compilation alongside Native whole-program optimization.
The optional [IDE plugin](../winrt-compiler-plugin/ide/README.md) supplies project templates,
Kotlin/XAML analysis and navigation, and JVM Run/Debug with development Hot Reload.
Version 0.1.0 release assets include separate packages for the supported IntelliJ
IDEA and Android Studio distributions. XAML Preview is temporarily disabled;
Native Hot Reload transport is not implemented.

The [project release workflow](../.github/workflows/release.yml) builds signed JVM
and Native packages when a stable project tag is pushed. See the [root README](../README.md)
for actual Gallery screenshots, downloads and installation requirements.

Upstream licenses and asset attribution remain in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
