# WebView2 Browser Shell Design

## Goal

Refine the existing runnable WebView2 sample into a native WinUI single-tab browser shell. The shell should follow the supplied browser reference closely while preserving Windows caption buttons, using projected Windows App SDK controls, and keeping the sample a validation-only consumer of existing runtime and projection contracts.

## Reference And Ownership

`.cswinrt/src/Projections/WinAppSDK` and `.cswinrt/src/Samples/WinUIDesktopSample` remain the projection and application-host references. Microsoft WinUI guidance supplies the `TabView`, custom title-bar, XAML resource, Mica, and responsive layout behavior. Kotlin ownership remains entirely in `winrt-samples`: this slice selects additional generated Windows App SDK types and composes them in `WebView2Sample`; it does not add runtime, metadata, generator, activation, marshaling, or packaging behavior.

The existing `WinUiXamlComponentResources` helper is outside this sample's resource path. Git history shows that helper was introduced for third-party component resource dictionaries used by authored controls such as `SettingsCard` and `Shimmer`. Standard WinUI controls require only a directly constructed `XamlControlsResources` dictionary.

## Shell Composition

The window extends content into a custom title bar while retaining native Windows minimize, maximize, and close buttons. A `TabView` occupies the application-controlled title-bar area and contains exactly one non-closable `TabViewItem`. The add-tab button is hidden. Empty title-bar space remains draggable and the layout reserves the native caption-button region.

The selected tab represents the embedded WebView2 browser. Below the tab strip, the browser surface contains:

- A compact navigation toolbar with Back, Forward, Reload, Home, a horizontally stretching address box, and Go.
- A status row that is visible during initialization, navigation, and failures, then collapses after successful navigation.
- A WebView2 content row that consumes all remaining width and height.

Buttons use Fluent symbols rather than text where a familiar icon exists. Icon-only controls receive tooltips and accessible names. The address box remains the dominant flexible control, while the toolbar and title bar use WinUI theme resources instead of hard-coded light-only colors.

## Resources And Mica

During launch, the sample directly installs the standard control resources with the equivalent of:

```kotlin
Application.current.resources.mergedDictionaries.add(XamlControlsResources())
```

It does not call `WinUiXamlComponentResources.installInto(...)` and does not add a wrapper around standard `XamlControlsResources` construction.

The window uses `MicaBackdrop` as its long-lived system backdrop. Existing diagnostic behavior is preserved through a `kotlin.winrt.samples.skipMica` option so launch and rendering failures can still be isolated without changing the normal visual path. WinUI theme-aware resources provide foregrounds and control surfaces for light, dark, and high-contrast behavior.

## Responsive Layout

The fixed WebView2 width and height are removed. A root `Grid` owns vertical layout through auto-sized title-bar, toolbar, and status rows plus one star-sized WebView2 row. The address field uses star-width column sizing, so both the toolbar and WebView2 track the current client area without manual resize handlers.

At narrower widths the nonessential Home command is hidden while Back, Forward, Reload, address entry, and Go remain available. Stable row and column definitions prevent status visibility or navigation-state changes from resizing unrelated controls.

## Navigation And Lifetime

The existing embedded startup document, HTTP/HTTPS address normalization, initialization ordering, error retention, automatic smoke exit, and owned-resource cleanup remain intact. Home navigates to the sample's default address. Pressing Go or submitting the address invokes the same normalization and navigation path.

Window, button, and WebView2 subscriptions continue to use publisher-owned event lifetime through ordinary `+=` registration. The sample retains no `EventRegistrationToken`, performs no explicit event removal, and adds no `-=` cleanup. Explicitly owned asynchronous initialization and WebView2 resources remain closed through the existing idempotent cleanup path.

## Projection Inputs

`winrt-samples` selects the exact Windows App SDK types required by the shell, including `XamlControlsResources`, `MicaBackdrop`, `TabView`, `TabViewItem`, grid layout types, symbol/icon types, tooltips, visibility and automation helpers, plus any directly referenced title-bar or input event argument types. Dependency closure remains responsible for supporting interfaces. No generated projection file is edited by hand.

## Validation

Source-level JVM tests cover the resource-installation boundary, shell configuration helpers, responsive visibility decision, address submission behavior, and the absence of sample-owned event-token cleanup. Existing WebView2 normalization, failure containment, and cleanup tests remain green. Both JVM and `mingwX64` sample compilations must pass.

Runtime validation launches the real sample, confirms a responsive window titled `Kotlin WinRT WebView2`, verifies successful WebView2 initialization and embedded navigation, and visually checks the single-tab title bar, native caption buttons, Mica surface, toolbar, and status behavior. The running window is resized through wide, medium, and narrow states to confirm the WebView2 fills the remaining client area and Home hides without overlapping or clipping core controls. The final verified sample instance remains running for user inspection.
