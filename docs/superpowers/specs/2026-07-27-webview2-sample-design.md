# WebView2 Sample Design

## Goal

Add a runnable WebView2 example to `winrt-samples` that demonstrates the generated WinUI control and WebView2 Core event surfaces on both JVM and `mingwX64`. The sample must remain a validation-only consumer of existing runtime, projection, activation, and runtime-asset staging contracts.

## Reference And Ownership

`.cswinrt/src/Projections/WinAppSDK` establishes the WinUI projection boundary, while `.cswinrt/src/Samples/WinUIDesktopSample` demonstrates the Windows App SDK application-host shape and carries the WebView2 package dependency. Kotlin already owns `Microsoft.Web.WebView2.Core` in `winrt-projections:windows-webview2`, owns `Microsoft.UI.Xaml.Controls.WebView2` in `winrt-projections:windows-app-sdk`, and stages `WebView2Loader.dll` through the KWINRT-052 NuGet runtime-asset path. Therefore this slice changes only `winrt-samples`, its documentation, and its validation.

## Considered Approaches

1. Create a new top-level sample module. This makes the example independently addressable but duplicates substantial WinUI host, NuGet, resource, and JVM/native packaging configuration.
2. Add WebView2 directly to the existing controls gallery. This minimizes wiring but makes the WebView2 lifecycle and launch command difficult to understand in isolation.
3. Add a dedicated `WebView2Sample` entry inside the existing `winrt-samples` application. This reuses the proven dual-target host while keeping the example source and opt-in run path independent.

Use approach 3. A dedicated module can be introduced later only if a real packaging or publication boundary requires it.

## Application Shape

`WebView2Sample` starts a WinUI `Application`, creates one `Window`, and composes a vertical surface containing a horizontal navigation toolbar, a status label, and a fixed-size `WebView2`. The toolbar supports back, reload, address entry, and navigation. The initial document is embedded HTML so startup and smoke validation do not require network access; users can then enter an HTTP or HTTPS URI.

An explicit `kotlin.winrt.samples.runWebView2Sample` option selects this sample from the existing WinUI entry point. The option is propagated as a JVM system property and a native environment variable by the existing run tasks. Normal sample behavior remains unchanged when the option is absent.

## Events And Lifetime

The sample subscribes to core-initialized, navigation-started, navigation-completed, and window-closed events before loading content. These subscriptions have the same lifetime as their `Window`, `Button`, or `WebView2` publisher, so the sample neither retains `EventRegistrationToken` values nor explicitly unsubscribes during shutdown. The projected event source and publisher teardown own the underlying ABI tokens, matching the ordinary event usage in `.cswinrt/src/Samples/WinUIDesktopSample`; any failure of that ownership contract belongs in `winrt-runtime`, not in sample-local cleanup.

After the window is activated the sample calls `EnsureCoreWebView2Async`; only a successful `CoreWebView2Initialized` event enables navigation commands and calls `NavigateToString`. This follows the WinUI control contract that `NavigateToString` requires a non-null `CoreWebView2`. Navigation completion updates status and, when `autoExitWinUi` is enabled, closes WebView2 before exiting the application so smoke runs terminate deterministically without calling the public `Close` API after XAML shutdown. `Application.start` returning only clears the retained Kotlin application reference. Public WebView2 closure is restricted to launch-failure handling, automatic exit while XAML is live, and `Window.Closed`.

Every application, event, window, and click callback executes inside one `Exception` boundary. A callback failure is recorded and triggers an in-loop cleanup/exit attempt rather than crossing the projected delegate boundary as an HRESULT. The idempotent `close()` path snapshots only the owned initialization action and WebView2 control, attempts both closes, retains the first cleanup exception, suppresses a later exception, and clears Kotlin references in `finally`.

Invalid or blank addresses remain in the UI with a concise status message rather than throwing from the click callback. Scheme-less input, including host-and-port input, is normalized to HTTPS; explicit non-HTTP schemes are rejected. WebView initialization or navigation failures are surfaced through the generated event arguments and status text. Interactive WebView failures remain open and status-only. Auto-exit WebView failures are retained before status rendering, so a failing status assignment cannot erase the original smoke failure or prevent cleanup and exit. Launch, cleanup, status-rendering, and callback failures are retained unconditionally and rethrown after the application loop returns. No sample-local activation fallback is added.

## Projection Inputs

The sample's WinRT type selection adds `Microsoft.UI.Xaml.Controls.WebView2` and the directly consumed WebView2 event argument types. Metadata dependency closure supplies `Microsoft.Web.WebView2.Core` and `Windows.Foundation.Uri`. No handwritten projection or runtime helper is introduced.

The documented Gradle JVM and `mingwX64` launch tasks set WebView2's supported `WEBVIEW2_USER_DATA_FOLDER` override to target-specific directories under `build/kotlin-winrt/webview2-user-data`. The unpackaged native default would otherwise create `{executable}.WebView2` inside the tracked staged application output; a WebView2 child process can briefly retain cache-file handles after the application exits and make the next Gradle output snapshot fail. Runtime browser data therefore stays outside `stageWinRTApplicationPackage` ownership without changing projection or application-lifetime behavior.

## Validation

Add source-level JVM coverage for sample selection, URI normalization, generated WebView2 metadata availability, callback failure containment, and owned-resource cleanup ordering. Run targeted `winuiJvmTest`, JVM compilation, and `mingwX64` compilation. Run both JVM and `mingwX64` WebView2 host smoke with embedded HTML and automatic exit to validate publisher-owned event teardown without explicit unsubscription. The broader sample smoke remains the final integration gate if its runtime cost is practical.

Final evidence covers all 10 focused JVM tests and both target compilations. Fresh `--rerun-tasks` Native-to-JVM automatic-exit runs executed 45 and 47 Gradle tasks, logged core initialization, embedded-page request, and successful navigation, and left no `{executable}.WebView2` directory in the staged JVM application output while both target-specific external user-data directories existed. A direct Native release launch reached a responsive `Kotlin WinRT WebView2` window with a non-zero handle and exited with code 0 within 30 seconds of `CloseMainWindow()`. Final review also drove a RED/GREEN boundary case for scheme-like `letters:digits` input: only `localhost`, dotted hosts/IPs, and bracketed IPv6 remain eligible for no-scheme `host:port` normalization, while `file:443`, `about:123`, and `mailto:123` are rejected as explicit non-HTTP schemes.
