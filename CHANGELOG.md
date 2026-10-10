# Changelog

## [0.1.0]

The first Kotlin/WinRT release introduces a Kotlin language projection for Windows Runtime and WinUI 3. Build Windows applications with native controls using Kotlin/JVM or Kotlin/Native `mingwX64`, with runtime and projection behavior guided by Microsoft's CsWinRT and C++/WinRT implementations.

### What is included

- **WinRT runtime:** ABI calls, COM object identity and lifetime, activation, HRESULT/GUID/HSTRING handling, marshaling, generic interface signatures, delegates, collections, async bridges and Windows App SDK hosting for the implemented projection surface.
- **Metadata and generated APIs:** real WinMD ingestion, normalized metadata and Kotlin source generation for Windows SDK and NuGet components. Prebuilt Windows SDK, Windows.UI.Xaml, WebView2 and Windows App SDK projections retain their metadata baseline versions.
- **Kotlin authoring and compiler integration:** authored WinRT types, interface implementations, generated call sites, component hosting and native exports, with compiler artifacts for Kotlin 2.4.0 and 2.4.20.
- **WinUI XAML with Kotlin code-behind:** adjacent XAML/Kotlin files, typed named elements, event handlers, compiled bindings, styles, templates, resources, custom Kotlin types and XAML-declared properties. The pinned Kotlin-aware compiler produces XBF, with PRI and package staging handled by the toolkit.
- **Windows toolkit Gradle plugin:** automatic WinApp CLI provisioning, NuGet restore, projection generation, native payload staging, JVM launchers, Native executables, application resources, packaged development runs and MSIX packaging/signing. Resolve `0.1.0` from Maven Central using the plugin ID mapping in the README; Gradle Plugin Portal publication is not required.
- **IDE plugins:** separate packages for IntelliJ IDEA 2026.2.2, Android Studio Quail 4 and Android Studio Rabbit 2 Canary 2. Includes project/module templates, a bundled toolchain, Kotlin/XAML analysis and navigation, Gradle integration, JVM run/debug and development Hot Reload.
- **Kotlin WinUI Gallery:** shared JVM/Native application sources, 122 routes, 355 example documents, interactive controls, navigation/search and a selectable XAML/Kotlin source viewer. Layouts and assets are ported from the official WinUI Gallery with attribution; the application uses Windows App SDK 2.5.1.

### Downloads and requirements

Release assets contain signed JVM and Native Gallery MSIX packages, three IDE plugin ZIPs, the public Gallery signing certificate and `SHA256SUMS.txt`. Install one Gallery variant at a time; JVM and Native packages share one application identity. Install the matching Windows App Runtime for the framework-dependent packages and trust the verified signing certificate if it is not already trusted.

Core libraries and the Windows toolkit are published as `0.1.0` under `io.github.compose-fluent` on Maven Central. Prebuilt projection versions retain their SDK/metadata version, with dependencies on the `0.1.0` runtime. Gradle builds require JDK 25, a supported Kotlin compiler, Windows SDK and Windows C++ build tools. See the README for setup and examples.

### Initial-release boundaries

This release does not claim complete parity with every WinRT API or IDE distribution. Runtime and application sources support JVM and `mingwX64` for the implemented surface; IDE development transport and Hot Reload currently target JVM. XAML Preview is temporarily disabled. Hot Reload supports a bounded set of edits; other XAML changes and Kotlin code changes require rebuilding and restarting. The prebuilt Windows App SDK projection targets 2.2.0, while the Gallery and IDE templates generate projections for 2.5.1.

[Source, usage instructions and third-party notices](https://github.com/compose-fluent/kotlin-winrt/tree/v0.1.0).
