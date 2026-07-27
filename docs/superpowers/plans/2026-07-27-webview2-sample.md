# WebView2 Sample Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in, runnable WebView2 browser sample to the existing dual-target WinUI sample application.

**Architecture:** Keep application hosting, Windows App SDK deployment, NuGet runtime assets, and JVM/native packaging in the existing `winrt-samples` module. Add one focused `WebView2Sample` application surface, select it through an existing-platform-option pattern, and consume only generated WinUI/WebView2 APIs.

**Tech Stack:** Kotlin Multiplatform 2.4, generated Kotlin/WinRT projections, WinUI 3 from Windows App SDK 2.2.0, WebView2 1.0.3719.77, JUnit 4, Gradle on Windows.

## Global Constraints

- Keep `winrt-samples` validation-only; do not add activation, marshaling, projection, host, or packaging fallbacks.
- Preserve default `WinUiControlsSample` behavior when `kotlin.winrt.samples.runWebView2Sample` is absent.
- Support both JVM and `mingwX64` through shared `winuiMain` code.
- Use embedded HTML for deterministic startup; interactive navigation accepts HTTP and HTTPS only.
- Update `PLAN.md` with every implementation/status change and commit coherent slices immediately.

---

### Task 1: Lock The Selection And Address Contracts

**Files:**
- Create: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Create: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `winrt-samples/build.gradle.kts`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `winRTSampleOption(name: String): Boolean` and generated `WebView2.Metadata.TYPE_NAME`.
- Produces: `internal fun shouldRunWebView2Sample(): Boolean` and `internal fun normalizeWebView2Address(address: String): String?`.

- [ ] **Step 1: Write the failing contract tests**

```kotlin
class WebView2SampleTest {
    @Test
    fun normalizes_supported_addresses() {
        assertEquals("https://example.com", normalizeWebView2Address(" example.com "))
        assertEquals("http://localhost:8080", normalizeWebView2Address("http://localhost:8080"))
        assertEquals("https://openai.com", normalizeWebView2Address("https://openai.com"))
    }

    @Test
    fun rejects_blank_and_unsupported_addresses() {
        assertNull(normalizeWebView2Address("  "))
        assertNull(normalizeWebView2Address("file:///C:/private.html"))
    }

    @Test
    fun generated_webview2_surface_is_available() {
        assertEquals("Microsoft.UI.Xaml.Controls.WebView2", WebView2.Metadata.TYPE_NAME)
        assertEquals(
            "Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs",
            CoreWebView2NavigationCompletedEventArgs.Metadata.TYPE_NAME,
        )
    }
}
```

- [ ] **Step 2: Run the test to verify RED**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: Kotlin compilation fails because `normalizeWebView2Address` and the selected WebView2 projection surface do not yet exist in the sample compilation.

- [ ] **Step 3: Add the minimal contracts and projection inputs**

Add `type("Microsoft.UI.Xaml.Controls.WebView2")`, `type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationStartingEventArgs")`, and `type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs")` to `winRT`. Define option lookup and normalization as:

```kotlin
internal fun shouldRunWebView2Sample(): Boolean =
    winRTSampleOption("kotlin.winrt.samples.runWebView2Sample")

internal fun normalizeWebView2Address(address: String): String? {
    val trimmed = address.trim()
    if (trimmed.isEmpty()) return null
    val normalized = if ("://" in trimmed) trimmed else "https://$trimmed"
    return normalized.takeIf {
        it.startsWith("https://", ignoreCase = true) ||
            it.startsWith("http://", ignoreCase = true)
    }
}
```

- [ ] **Step 4: Run the focused test to verify GREEN**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: all three tests pass.

- [ ] **Step 5: Commit the contract slice**

```powershell
git add -- PLAN.md winrt-samples/build.gradle.kts winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt
git commit -m "test(samples): define WebView2 sample contracts"
```

### Task 2: Implement And Validate The Runnable Surface

**Files:**
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WinUiSampleEntry.kt`
- Modify: `winrt-samples/build.gradle.kts`
- Modify: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Modify: `README.md`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: `normalizeWebView2Address(address: String): String?`, `Application.start`, generated `WebView2`, `Uri`, event tokens, and the existing `autoExitWinUi` option.
- Produces: `object WebView2Sample { fun start() }` and a documented `kotlin.winrt.samples.runWebView2Sample` JVM/native launch option.

- [ ] **Step 1: Extend the test with sample selection**

```kotlin
@Test
fun reads_webview2_sample_selection() {
    val name = "kotlin.winrt.samples.runWebView2Sample"
    val previous = System.getProperty(name)
    try {
        System.setProperty(name, "true")
        assertTrue(shouldRunWebView2Sample())
    } finally {
        if (previous == null) System.clearProperty(name) else System.setProperty(name, previous)
    }
}
```

- [ ] **Step 2: Run the focused test to verify the new assertion**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: the selection test passes against Task 1's contract while the runnable surface is still absent.

- [ ] **Step 3: Implement the WinUI application surface**

Create `WebView2SampleApp : Application(), AutoCloseable` with one retained `Window`, one retained `WebView2`, retained navigation/click event tokens, and these behaviors:

```kotlin
override fun onLaunched(args: LaunchActivatedEventArgs) {
    val browser = WebView2().apply { width = 960.0; height = 600.0 }
    val address = TextBox().apply { text = "https://example.com"; width = 620.0 }
    val status = TextBlock().apply { text = "Loading embedded page..." }
    // Register navigation events before assigning content, build Back/Reload/Go handlers,
    // activate the Window, then call browser.navigateToString(INITIAL_HTML).
}
```

Navigation-started reports the requested URI. Navigation-completed reports success/error, refreshes back-button state, and calls `Application.current.exit()` when `kotlin.winrt.samples.autoExitWinUi` is true. `close()` removes every retained event token, calls `WebView2.close()`, and clears retained references.

Route `runWinUiSample()` to `WebView2Sample.start()` only when `shouldRunWebView2Sample()` is true; otherwise retain `WinUiControlsSample.start()`.

- [ ] **Step 4: Propagate the run option and document commands**

Add `kotlin.winrt.samples.runWebView2Sample` to `sampleJvmOptionProperties`. Add these focused commands to README:

```powershell
.\gradlew.bat :winrt-samples:runWinRTApplicationHost -Dkotlin.winrt.samples.runWebView2Sample=true
.\gradlew.bat :winrt-samples:runReleaseExecutableMingwX64 -Dkotlin.winrt.samples.runWebView2Sample=true
```

- [ ] **Step 5: Run static target validation**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest :winrt-samples:compileKotlinWinuiJvm :winrt-samples:compileKotlinMingwX64`

Expected: tests pass and both target compilations succeed with generated WebView2 control/Core references.

- [ ] **Step 6: Run the JVM WebView2 smoke**

Run: `.\gradlew.bat :winrt-samples:runWinRTApplicationHost -Dkotlin.winrt.samples.runWebView2Sample=true -Dkotlin.winrt.samples.autoExitWinUi=true`

Expected: the host logs WebView2 navigation completion and exits successfully. If the machine lacks the Evergreen WebView2 Runtime, record that external prerequisite without adding a fallback.

- [ ] **Step 7: Close PLAN status and commit**

Mark `WebView2-Sample` complete with exact validation evidence.

```powershell
git add -- PLAN.md README.md winrt-samples/build.gradle.kts winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WinUiSampleEntry.kt winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt
git commit -m "feat(samples): add WebView2 browser sample"
```
