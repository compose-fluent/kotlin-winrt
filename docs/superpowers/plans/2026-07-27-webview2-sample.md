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
- Produces: `internal fun normalizeWebView2Address(address: String): String?`.

- [x] **Step 1: Write the failing contract tests**

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

- [x] **Step 2: Run the test to verify RED**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: Kotlin compilation fails because `normalizeWebView2Address` and the selected WebView2 projection surface do not yet exist in the sample compilation.

- [x] **Step 3: Add the minimal contracts and projection inputs**

Add `type("Microsoft.UI.Xaml.Controls.WebView2")`, `type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationStartingEventArgs")`, and `type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs")` to `winRT`. Define normalization as:

```kotlin
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

- [x] **Step 4: Run the focused test to verify GREEN**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: all three tests pass.

- [x] **Step 5: Commit the contract slice**

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
- Consumes: `normalizeWebView2Address(address: String): String?`, `Application.start`, generated `WebView2`, `Uri`, projected event surfaces, and the existing `autoExitWinUi` option.
- Produces: `internal fun shouldRunWebView2Sample(): Boolean`, `object WebView2Sample { fun start() }`, and a documented `kotlin.winrt.samples.runWebView2Sample` JVM/native launch option.

- [x] **Step 1: Extend the test with sample selection**

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

- [x] **Step 2: Run the focused test to verify the new assertion**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest`

Expected: the selection test passes against Task 1's contract while the runnable surface is still absent.

- [x] **Step 3: Implement the WinUI application surface**

Create `WebView2SampleApp : Application(), AutoCloseable` with one retained `Window`, one retained `WebView2`, publisher-owned navigation/click event subscriptions, and these behaviors:

```kotlin
override fun onLaunched(args: LaunchActivatedEventArgs) {
    val browser = WebView2().apply { width = 960.0; height = 600.0 }
    val address = TextBox().apply { text = "https://example.com"; width = 620.0 }
    val status = TextBlock().apply { text = "Loading embedded page..." }
    // Register core-initialized and navigation events before assigning content, build
    // Back/Reload/Go handlers, activate the Window, then request core initialization.
    // Enable navigation and call browser.navigateToString(INITIAL_HTML) only after
    // CoreWebView2Initialized reports a non-null CoreWebView2.
}
```

Navigation-started reports the requested URI. Navigation-completed reports success/error and refreshes back-button state. Interactive failures remain status text. In auto-exit mode, failures are retained for propagation after `Application.start` returns; success or failure closes WebView2 while XAML is still active and then calls `Application.current.exit()`. `Window.Closed` uses the same idempotent cleanup path. Event subscriptions end with their `Window`, `Button`, or `WebView2` publisher and are not explicitly removed. `close()` closes the retained initialization action, calls `WebView2.close()`, and clears retained references without silently discarding cleanup failures.

Route `runWinUiSample()` to `WebView2Sample.start()` only when `shouldRunWebView2Sample()` is true; otherwise retain `WinUiControlsSample.start()`.

- [x] **Step 4: Propagate the run option and document commands**

Add `kotlin.winrt.samples.runWebView2Sample` to `sampleJvmOptionProperties`. Add these focused commands to README:

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" :winrt-samples:runWinRTApplicationHost
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" :winrt-samples:runReleaseExecutableMingwX64
```

- [x] **Step 5: Run static target validation**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest :winrt-samples:compileKotlinWinuiJvm :winrt-samples:compileKotlinMingwX64`

Expected: tests pass and both target compilations succeed with generated WebView2 control/Core references.

- [x] **Step 6: Run real JVM and `mingwX64` WebView2 smoke**

Run: `& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runWinRTApplicationHost`

Run: `& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runReleaseExecutableMingwX64`

Expected: the host logs WebView2 navigation completion and exits successfully. If the machine lacks the Evergreen WebView2 Runtime, record that external prerequisite without adding a fallback.

Validation: both JVM `runWinRTApplicationHost` and native `runReleaseExecutableMingwX64` logged `core initialized`, `embedded page requested`, and `navigation completed success=true`, then completed with `BUILD SUCCESSFUL`. A separate interactive native launch reached the `Kotlin WinRT WebView2` window and returned exit code 0 after a normal window close, exercising the `Window.Closed` cleanup path.

### Task 3: Harden Callback And Shutdown Failure Paths

**Files:**
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Modify: `docs/superpowers/specs/2026-07-27-webview2-sample-design.md`
- Modify: `PLAN.md`

**Interfaces:**
- Produces: one callback `Exception` boundary, attempt-all owned-resource cleanup, ordered failure recording, and in-loop WebView2 closure only.

- [x] **Step 1: Reproduce cleanup and failure-policy gaps with focused tests**

Verify RED for a missing attempt-all cleanup executor, smoke failure recording before status rendering, continued automatic exit after status-rendering failure, interactive status-only failure handling, and callback exception containment.

- [x] **Step 2: Implement the failure boundaries and lifecycle correction**

Execute all cleanup actions while preserving the first exception and suppressing later exceptions. Route the application initializer, `onLaunched`, WebView2 events, window close, and button clicks through one callback boundary. Record fatal launch, callback, cleanup, exit, and status-rendering failures unconditionally. Do not call `WebView2.close()` after `Application.start` returns; close it only on launch failure, automatic exit, or `Window.Closed` while XAML is live.

- [x] **Step 3: Verify focused GREEN**

All nine `WebView2SampleTest` JVM tests pass after the complete callback and lifecycle wiring.

- [x] **Step 4: Re-run both real smoke targets and interactive native close**

Run fresh JVM and `mingwX64` automatic-exit smoke, verify the expected WebView2 initialization/navigation logs, then launch the native interactive sample, confirm the `Kotlin WinRT WebView2` window, and leave it running for user inspection.

Validation: the fresh combined gate completed in 3m43s with all nine focused JVM tests available and both real targets logging `core initialized`, `embedded page requested`, and `navigation completed success=true`. A separate native interactive launch reported the responsive title `Kotlin WinRT WebView2` and returned exit code 0 after `CloseMainWindow()`. The final user-visible instance is launched only after all source and documentation edits are complete.

### Task 4: Use Publisher-Owned Event Lifetime

**Files:**
- Modify: `winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt`
- Modify: `winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt`
- Modify: `docs/superpowers/specs/2026-07-27-webview2-sample-design.md`
- Modify: `PLAN.md`

**Interfaces:**
- Consumes: the `.cswinrt` event-source contract in which the publisher owns the native registration and releases it with the projected control.
- Produces: a sample with no `EventRegistrationToken` state or explicit `remove*` calls, plus `closeWebView2Resources(closeInitializationAction, closeWebView)` for ordered owned-resource cleanup.
- Produces: target-specific WebView2 user-data directories outside the tracked staged application output for both documented Gradle launch tasks.

- [x] **Step 1: Record the approved lifetime correction**

Update the design and plan so `Window`, `Button`, and `WebView2` events end with their publisher. Keep deterministic closure only for the retained initialization action and the public WebView2 `Close` operation.

- [x] **Step 2: Write the failing owned-resource cleanup test**

```kotlin
@Test
fun closes_only_owned_webview_resources_in_order() {
    val attempts = mutableListOf<String>()

    closeWebView2Resources(
        closeInitializationAction = { attempts += "initialization" },
        closeWebView = { attempts += "webview" },
    )

    assertEquals(listOf("initialization", "webview"), attempts)
}
```

- [x] **Step 3: Run the focused test and verify RED**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest --tests io.github.composefluent.winrt.samples.WebView2SampleTest.closes_only_owned_webview_resources_in_order`

Expected: Kotlin compilation fails because `closeWebView2Resources` does not exist.

- [x] **Step 4: Remove sample-owned event cleanup**

Implement `closeWebView2Resources` by passing only the non-null initialization and WebView close actions to the existing attempt-all executor. Subscribe inline without retaining returned tokens, remove the self-unsubscribe in `CoreWebView2Initialized`, and delete all event-token fields and `remove*` calls from `WebView2SampleApp.close()`.

The final interactive-to-smoke validation exposed WebView2's default `{executable}.WebView2` user-data folder inside `stageWinRTApplicationPackage` output. Configure `WEBVIEW2_USER_DATA_FOLDER` on the JVM and `mingwX64` Gradle launch tasks so a browser child process finishing after application exit cannot lock a tracked task output during the next staging snapshot.

- [x] **Step 5: Verify focused tests and both real targets**

Run: `.\gradlew.bat :winrt-samples:winuiJvmTest :winrt-samples:compileKotlinWinuiJvm :winrt-samples:compileKotlinMingwX64`

Run: `& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runWinRTApplicationHost`

Run: `& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=true" :winrt-samples:runReleaseExecutableMingwX64`

Expected: all focused tests and compilations pass; both real hosts log core initialization, embedded-page navigation, successful completion, and exit without explicit event unsubscription.

Validation: all 10 `WebView2SampleTest` cases passed with zero failures, errors, or skips, and the combined JVM/`mingwX64` compilation gate completed with `BUILD SUCCESSFUL`. Fresh `--rerun-tasks` Native-then-JVM automatic-exit runs executed 45 and 47 tasks, respectively; both logged `core initialized`, `embedded page requested`, and `navigation completed success=true`, then completed in 6m55s and 6m09s. The RED immediate rerun after an interactive Native close failed while Gradle snapshotted the default `{executable}.WebView2` UDF inside staged output; after assigning target-specific UDFs, the forced Native-then-JVM sequence passed with `TrackedUserDataExists=False`, `NativeUserDataExists=True`, and `JvmUserDataExists=True`. A direct Native release launch reported responsive title `Kotlin WinRT WebView2`, non-zero window handle `54137190`, and `Responding=True`; `CloseMainWindow()` returned true and the process exited within 30 seconds with code 0.

- [x] **Step 6: Resolve the final-review host-and-port ambiguity**

Add RED assertions for explicit `file:443`, `about:123`, and `mailto:123` schemes. Restrict the no-scheme `host:port` exception to `localhost`, dotted hosts/IPs, and bracketed IPv6 while retaining HTTPS normalization for those forms. The first new assertion failed against `https://file:443`; after the regex correction, all 10 focused JVM tests and `compileKotlinMingwX64` passed.

- [x] **Step 7: Close PLAN status and commit**

Mark `WebView2-Sample` complete with exact validation evidence.

```powershell
git add -- PLAN.md README.md docs/superpowers/plans/2026-07-27-webview2-sample.md docs/superpowers/specs/2026-07-27-webview2-sample-design.md winrt-samples/build.gradle.kts winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WinUiSampleEntry.kt winrt-samples/src/winuiMain/kotlin/io/github/composefluent/winrt/samples/WebView2Sample.kt winrt-samples/src/winuiJvmTest/kotlin/io/github/composefluent/winrt/samples/WebView2SampleTest.kt
git commit -m "feat(samples): add WebView2 browser sample"
```
