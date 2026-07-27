# WebView2 Browser Shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the existing WebView2 sample into a programmatically composed native WinUI single-tab browser shell with direct standard-control resources, Mica, responsive WebView2 sizing, and publisher-owned events.

**Architecture:** Keep browser lifecycle, navigation, failure containment, and cleanup in `WebView2Sample.kt`. Add a focused `WebView2BrowserShell.kt` that creates the `Grid`, `TabView`, toolbar controls, status text, and WebView2 entirely through projected Kotlin APIs, then returns those controls to the application layer for event binding.

**Tech Stack:** Kotlin Multiplatform 2.4, generated Kotlin/WinRT projections, WinUI 3 from Windows App SDK 2.2.0, WebView2 1.0.3719.77, JUnit 4, Gradle on Windows.

## Global Constraints

- Construct all UI programmatically in Kotlin; do not use `XamlReader`, embedded XAML markup, or `.xaml` files.
- Install standard resources directly with `Application.current.resources.mergedDictionaries.add(XamlControlsResources())` or its null-checked equivalent.
- Do not call `WinUiXamlComponentResources.installInto(...)`; that helper remains limited to authored third-party controls.
- Use one non-closable `TabViewItem`, hide the add-tab button, and retain native minimize, maximize, and close buttons.
- Put WebView2 in a star-sized `Grid` row with no fixed width or height and no manual WebView2 resize handler.
- Apply `MicaBackdrop` unless `kotlin.winrt.samples.skipMica` is enabled.
- Keep `Window`, `Button`, `TextBox`, and `WebView2` events publisher-owned: no retained `EventRegistrationToken`, no `remove*`, and no `-=` cleanup.
- Support shared `winuiMain` behavior on JVM and `mingwX64`.
- Update `PLAN.md` with every implementation/status change and commit each coherent slice immediately.

---

### Task 1: Define Browser-Shell Projection And Responsive Contracts

**Files:**
- Modify: `winrt-samples/build.gradle.kts`
- Create: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2BrowserShell.kt`
- Modify: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: generated WinUI metadata and `Visibility` value constants.
- Produces: `internal const val WEBVIEW2_HOME_MIN_WIDTH: Double` and `internal fun webView2HomeVisibility(windowWidth: Double): Visibility`.

- [x] **Step 1: Write the failing projection and responsive tests**

Add imports for `Grid`, `SymbolIcon`, `TabView`, `TabViewItem`, `Visibility`, `XamlControlsResources`, and `MicaBackdrop`, then add:

```kotlin
@Test
fun generated_browser_shell_surface_is_available() {
    assertEquals("Microsoft.UI.Xaml.Controls.Grid", Grid.Metadata.TYPE_NAME)
    assertEquals("Microsoft.UI.Xaml.Controls.TabView", TabView.Metadata.TYPE_NAME)
    assertEquals("Microsoft.UI.Xaml.Controls.TabViewItem", TabViewItem.Metadata.TYPE_NAME)
    assertEquals("Microsoft.UI.Xaml.Controls.SymbolIcon", SymbolIcon.Metadata.TYPE_NAME)
    assertEquals("Microsoft.UI.Xaml.Controls.XamlControlsResources", XamlControlsResources.Metadata.TYPE_NAME)
    assertEquals("Microsoft.UI.Xaml.Media.MicaBackdrop", MicaBackdrop.Metadata.TYPE_NAME)
}

@Test
fun hides_home_below_the_narrow_width_boundary() {
    assertEquals(Visibility.Collapsed, webView2HomeVisibility(WEBVIEW2_HOME_MIN_WIDTH - 1.0))
    assertEquals(Visibility.Visible, webView2HomeVisibility(WEBVIEW2_HOME_MIN_WIDTH))
}
```

- [x] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest
```

Expected: compilation fails because the new projected types and responsive helper are not selected or defined.

- [x] **Step 3: Select the exact generated WinUI types**

Add exact `type(...)` entries for:

```kotlin
type("Microsoft.UI.Xaml.GridLength")
type("Microsoft.UI.Xaml.GridUnitType")
type("Microsoft.UI.Xaml.Visibility")
type("Microsoft.UI.Xaml.Automation.AutomationProperties")
type("Microsoft.UI.Xaml.Controls.ColumnDefinition")
type("Microsoft.UI.Xaml.Controls.Grid")
type("Microsoft.UI.Xaml.Controls.RowDefinition")
type("Microsoft.UI.Xaml.Controls.Symbol")
type("Microsoft.UI.Xaml.Controls.SymbolIcon")
type("Microsoft.UI.Xaml.Controls.TabView")
type("Microsoft.UI.Xaml.Controls.TabViewItem")
type("Microsoft.UI.Xaml.Controls.TabViewWidthMode")
type("Microsoft.UI.Xaml.Controls.ToolTipService")
type("Microsoft.UI.Xaml.Input.KeyEventHandler")
type("Microsoft.UI.Xaml.Input.KeyRoutedEventArgs")
type("Windows.System.VirtualKey")
```

Do not add a broad namespace projection or handwritten projection file.

- [x] **Step 4: Add the minimal responsive helper**

Create `WebView2BrowserShell.kt` with:

```kotlin
internal const val WEBVIEW2_HOME_MIN_WIDTH = 720.0

internal fun webView2HomeVisibility(windowWidth: Double): Visibility =
    if (windowWidth >= WEBVIEW2_HOME_MIN_WIDTH) Visibility.Visible else Visibility.Collapsed
```

- [x] **Step 5: Run the focused test and verify GREEN**

Run the command from Step 2.

Expected: all `WebView2SampleTest` cases pass.

Validation: all 12 focused tests passed with zero failures, errors, or skips after the sample projection regenerated in 6m56s.

- [x] **Step 6: Commit the contract slice**

```powershell
git add -- PLAN.md winrt-samples/build.gradle.kts winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2BrowserShell.kt winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt
git commit -m "test(samples): define WebView2 browser shell contracts"
```

### Task 2: Build And Bind The Programmatic WinUI Shell

**Files:**
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2BrowserShell.kt`
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `webView2HomeVisibility(windowWidth: Double): Visibility`, generated WinUI controls, existing address normalization, callback containment, and owned-resource cleanup.
- Produces: `internal data class WebView2BrowserShell` and `internal fun createWebView2BrowserShell(): WebView2BrowserShell`.

- [ ] **Step 1: Expand the shell file with one programmatic composition boundary**

Define a returned control bundle:

```kotlin
internal data class WebView2BrowserShell(
    val root: Grid,
    val titleBar: TabView,
    val address: TextBox,
    val status: TextBlock,
    val back: Button,
    val forward: Button,
    val reload: Button,
    val home: Button,
    val go: Button,
    val webView: WebView2,
)
```

Build a root `Grid` with four rows: 48-pixel title bar, auto toolbar, auto status, and one-star WebView2 content. Build the toolbar with auto columns for Back, Forward, Reload, and Home, one star column for the address box, and one auto column for Go. Construct tracks with:

```kotlin
GridLength(48.0, GridUnitType.Pixel)
GridLength(1.0, GridUnitType.Auto)
GridLength(1.0, GridUnitType.Star)
```

Create one `TabViewItem` with `header = "Kotlin WinRT"` and `isClosable = false`; configure `TabView` with `isAddTabButtonVisible = false`, `tabWidthMode = TabViewWidthMode.SizeToContent`, and drag/reorder/tear-out disabled. Add the item through `tabView.tabItems.add(tabItem)`.

Create 40x40 icon buttons with `SymbolIcon(Symbol.Back)`, `Symbol.Forward`, `Symbol.Refresh`, `Symbol.Home`, and `Symbol.Go`. Apply `ToolTipService.setToolTip(button, label)` and `AutomationProperties.setName(button, label)`. Set the address field to stretch with a 120-pixel minimum width. Do not assign `width` or `height` to WebView2.

- [ ] **Step 2: Install resources and configure the real window**

At the start of `WebView2SampleApp.launch()`, directly add `XamlControlsResources()` to the current application's merged dictionaries. Create the window, set `title`, and configure:

```kotlin
mainWindow.systemBackdrop = MicaBackdrop()
mainWindow.extendsContentIntoTitleBar = true
mainWindow.content = shell.root
mainWindow.setTitleBar(shell.titleBar)
mainWindow.appWindow.resizeClient(SizeInt32(1200, 800))
```

Skip only the Mica assignment when `kotlin.winrt.samples.skipMica` is enabled. Do not call the component-resource helper.

- [ ] **Step 3: Bind navigation, status, keyboard, and responsive behavior**

Replace the old fixed-size controls with the returned shell controls. Keep all existing callback boundaries. Add Home navigation through the existing `navigate` path. Add Enter-key submission with `KeyEventHandler`; when `eventArgs.key == VirtualKey.Enter`, set `eventArgs.handled = true` and invoke the same navigation path as Go.

On navigation start, set status text and `Visibility.Visible`. On successful completion, set `Visibility.Collapsed`; on initialization or navigation failure, set the failure text and `Visibility.Visible`. Subscribe to `mainWindow.sizeChanged` and assign:

```kotlin
shell.home.visibility = webView2HomeVisibility(eventArgs.size.width)
```

Every new subscription uses `+=` without retaining a token or adding explicit removal.

- [ ] **Step 4: Verify focused tests and both target compilations**

Run:

```powershell
.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest :winrt-samples:compileKotlinWinuiJvm :winrt-samples:compileKotlinMingwX64 --max-workers=1 --console=plain
```

Expected: focused tests pass and both targets compile the same `winuiMain` programmatic shell.

- [ ] **Step 5: Run the JVM automatic-exit smoke**

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runWinRTApplicationHost --max-workers=1 --console=plain
```

Expected: logs contain `core initialized`, `embedded page requested`, and `navigation completed success=true`, then the task exits successfully.

### Task 3: Verify Native Runtime, Resize Behavior, And Final State

**Files:**
- Modify: `docs/superpowers/plans/2026-07-27-webview2-browser-shell.md`
- Modify: `PLAN.md`
- Modify only if verification exposes a defect: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2BrowserShell.kt`
- Modify only if verification exposes a defect: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`

**Interfaces:**
- Consumes: completed shared shell and existing Gradle/native launch paths.
- Produces: objective JVM/native launch, visual, resize, and ownership evidence.

- [ ] **Step 1: Run the `mingwX64` automatic-exit smoke**

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runReleaseExecutableMingwX64 --max-workers=1 --console=plain
```

Expected: the native sample initializes WebView2, completes embedded navigation, and exits successfully.

- [ ] **Step 2: Launch the native interactive sample and verify the real window**

Launch the staged release executable with `kotlin.winrt.samples.runWebView2Sample=true`, `kotlin.winrt.samples.autoExitWinUi=false`, and the existing external `WEBVIEW2_USER_DATA_FOLDER`. Confirm a responsive process, non-zero main window handle, and title `Kotlin WinRT WebView2`.

- [ ] **Step 3: Resize and capture wide, medium, and narrow states**

Use the native window handle with `SetWindowPos` to check approximately 1200x800, 760x600, and 600x500 client-facing states. Capture each window with `System.Drawing.Graphics.CopyFromScreen`, inspect the PNGs, and confirm:

- one non-closable tab in the custom title bar;
- native Windows caption buttons remain visible and unobstructed;
- Mica is visible around the title/toolbar surface;
- address entry stretches while command buttons remain stable;
- Home is visible wide and collapsed narrow;
- WebView2 fills all space below the auto-sized chrome without clipping or stale fixed dimensions;
- status is collapsed after successful embedded navigation.

- [ ] **Step 4: Run source-policy checks**

```powershell
rg -n "XamlReader|WinUiXamlComponentResources|EventRegistrationToken|remove[A-Z]|-=|webView\.(width|height)" winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2*.kt
```

Expected: no XAML reader/markup helper, component-resource workaround, event-token cleanup, explicit event removal, or fixed WebView2 dimensions. The direct `XamlControlsResources()` construction and ordinary `+=` subscriptions remain visible in source review.

- [ ] **Step 5: Close the verification instance, update evidence, and commit**

Mark every completed plan checkbox, replace the `WebView2-Browser-Shell 正在做` item in `PLAN.md` with `[x]`, and record exact test counts, build results, smoke logs, window handle/title, and inspected resize dimensions. Then commit the complete implementation:

```powershell
git add -- PLAN.md docs/superpowers/plans/2026-07-27-webview2-browser-shell.md docs/superpowers/specs/2026-07-27-webview2-browser-shell-design.md winrt-samples/build.gradle.kts winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2BrowserShell.kt winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt
git commit -m "feat(samples): build WebView2 browser shell"
```

- [ ] **Step 6: Relaunch and leave the final verified instance running**

After the commit, launch the same native interactive executable again at the wide state, confirm it remains responsive, and leave it open for user inspection.
