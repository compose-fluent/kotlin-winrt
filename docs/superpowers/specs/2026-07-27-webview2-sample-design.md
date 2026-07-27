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

The sample subscribes to navigation-started and navigation-completed events before loading content. Navigation completion updates status and, when `autoExitWinUi` is enabled, exits the application so smoke runs terminate deterministically. Button handlers read only current control state. `close()` removes every registered handler, closes the WebView2 control, and releases window references; cleanup is idempotent enough for the existing application shutdown path.

Invalid or blank addresses remain in the UI with a concise status message rather than throwing from the click callback. Scheme-less input is normalized to HTTPS. WebView initialization or navigation failures are surfaced through the generated event arguments and status text; no sample-local activation fallback is added.

## Projection Inputs

The sample's WinRT type selection adds `Microsoft.UI.Xaml.Controls.WebView2` and the directly consumed WebView2 event argument types. Metadata dependency closure supplies `Microsoft.Web.WebView2.Core` and `Windows.Foundation.Uri`. No handwritten projection or runtime helper is introduced.

## Validation

Add source-level JVM coverage for sample selection, URI normalization, and generated WebView2 metadata availability. Run targeted `winuiJvmTest`, JVM compilation, and `mingwX64` compilation. Run the WebView2 JVM host smoke with embedded HTML and automatic exit when the local machine has a WebView2 runtime. The broader sample smoke remains the final integration gate if its runtime cost is practical.
