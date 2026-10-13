# Kotlin XAML compilation

The same pipeline serves WinUI 3 (`XamlFramework.WinUi`) and the Windows SDK's
UWP XAML (`XamlFramework.Uwp`). Setting `applicationModel` to
`WindowsApplicationModel.Uwp` supplies the UWP default; XAML libraries and projection
modules select it explicitly with `windows { xaml { framework.set(XamlFramework.Uwp) } }`.
The existing `winuiMain` source-set and `WinUiXaml` task names remain compatible and
do not select the framework. WinUI 2 controls also use the UWP framework, even
though their namespace is `Microsoft.UI.Xaml`. See the [UWP guide](../docs/UWP.md)
for complete application configuration and current runtime-validation limits.

The Windows toolkit discovers XAML beside Kotlin source files. A class-bearing
`views/MainPage.xaml` must have a sibling `views/MainPage.kt`; `x:Class` names the
Kotlin class, and its direct projected base must match the XAML root. No page
annotation or per-file Gradle registration is required.

```kotlin
class MainPage : Page() {
    override fun initializeComponent() {
        super.initializeComponent()
        // Named controls are now connected.
    }
    private fun onClick(sender: Any?, args: RoutedEventArgs) {
        output.text = "Clicked"
    }
}
```

The compiler plugin calls `initializeComponent()` after complete construction,
including secondary constructor bodies and constructor references. Consuming
Kotlin modules must enable the plugin as well. Named elements are generated
read-only instance properties. Access before connection fails explicitly. The
generated base initialization guards repeated and reentrant calls; a failed load
cannot be retried on the same instance. Ordinary
private event handlers are checked against the actual WinRT delegate signature.
An `x:Name` that conflicts with an existing property produces a source diagnostic;
it is never silently renamed.

Aggregated XAML pages use the WinRT composition and reference-tracking lifetime
contract on both targets. External COM references retain the Kotlin page, and
borrowed interface wrappers keep the native object alive until those wrappers
are released.

The build first analyzes declarations, then compiles isolated Kotlin semantic
symbols, generates XBF, and finally compiles application classes. Semantic
classes are never placed on the application's runtime classpath. XBF enters the
existing PRI staging pipeline with its relative resource path preserved.

For WinUI, the plugin pins the Windows x64 compiler release `0.1.0-preview.6`, protocol 3,
and archive SHA-256
`7718088e70e1d4e95446e0b1e6891fcce09ab70c82ba3ae6027d6aaa43fbc628`
from [the maintained fork](https://github.com/compose-fluent/microsoft-ui-xaml/releases/tag/kotlin-xamlc-v0.1.0-preview.6).
The first build downloads it; subsequent offline builds use a checksum-verified
Gradle user-home cache. WinUI's GenXbf comes from the resolved WinUI NuGet package.
WinUI applications do not need a compiler checkout or a stock XamlCompiler installed.

UWP has no default downloadable compiler release. Supply an unpacked UWP compiler
package through `compilerDirectory`, or explicitly select a UWP release version
and its archive SHA-256. The default UWP archive name is
`kotlin-xamlc-<version>-uwp-win-x64.zip`. A local package contains
`kotlin-xamlc.json` and the checksummed executable/dependencies, and declares UWP
plus the selected SDK version in its compatibility metadata. A WinUI package or
a raw MSBuild output does not satisfy that contract. UWP uses the Windows
SDK's GenXbf; discovery handles both `bin/<version>/XamlCompiler/x64` and older
`bin/<version>/x64` SDK layouts.

For fork development, `-PkotlinWinRT.xaml.compilerDirectory=<directory>` selects
an unpacked compiler and `-PkotlinWinRT.xaml.genXbfDirectory=<tools-directory>`
overrides GenXbf discovery. This directory contains `x64/GenXbf.dll`; do not pass
the DLL itself or its `x64` directory. A custom release requires both
`kotlinWinRT.xaml.version` and `kotlinWinRT.xaml.sha256`. The `windows.xaml` DSL
also exposes these settings and `archiveUrl` for a custom distribution endpoint.
Local `compilerDirectory` overrides do not need download version/checksum settings.
Compiler inputs, declaration indexes and cache contexts carry the selected
framework; mismatched compiler/declaration frameworks are rejected.

JVM and `mingwX64` use the same declaration and implementation contracts, with
separate semantic compilations and final outputs. The backend supports compiled
bindings, template scopes, phased bindings, converters, notifications and deferred
loading. Unsupported forms fail at build time; there is no runtime string-XAML
fallback for pages. Target-specific execution and real UI acceptance are tracked separately
in `winui-gallery/XAML_MIGRATION.md` for WinUI and the
[UWP sample](../winrt-samples/uwp-app/README.md) for UWP. Current WinUI IDE features
are described in the [IDE guide](../winrt-compiler-plugin/ide/README.md). UWP
designer/preview, Hot Reload and packaged `--debug-output` are not implemented.

## Library inputs and resources

Ordinary Kotlin model libraries publish a XAML schema and compiled typed
accessors through the existing Kotlin/WinRT library identity. Inferred public
property types are exported by an isolated semantic compilation. These models
do not have to be authored Windows Runtime components. Consumer compilations
receive the schema and runtime registrar for both JVM and Native.
Declarations with Kotlin `DeprecationLevel.ERROR` or `HIDDEN` are omitted,
including unavailable accessors, constructors and enum entries. Warning-level
declarations remain available.

The export runs for every library without XAML that applies the toolkit, and
compiles its sources a second time. A library whose classes no markup names
switches it off with `windows { xaml { exportLibrarySchema = false } }` or
`-PkotlinWinRT.xaml.exportLibrarySchema=false`.

Classless XAML dictionaries can live in a library's resource source sets. They
compile to XBF and enter the existing AppX resource variants without generating
SDK projections or synthetic page connectors. Their relative resource paths are
preserved, so existing `ms-appx:///` dictionary URIs continue to resolve. NuGet
metadata remains a compiler input when projection generation is disabled.
Renaming or removing the last XAML input removes only this pipeline's stale
generated code and intermediate files; model-library schemas are preserved.

## Properties declared in XAML

`x:Properties` generates ordinary Kotlin properties on the page class, following
the upstream C# backend. Reference properties are nullable until initialized;
value properties begin with their zero value. An inherited or named-element
collision is reported rather than renamed.

```xml
<x:Properties>
    <x:Property Name="Heading" Type="x:String" DefaultValue="Welcome" />
    <x:Property Name="Count" Type="x:Int32" IsReadOnly="True" DefaultValue="2" />
</x:Properties>
```

Writable properties notify only when their value changes. Subscribe with
`addHeadingChanged(handler)` and remove with `removeHeadingChanged(handler)`,
using the mapped `PropertyChangedEventHandler`. `ChangedHandler` overrides the
event name; OneWay and TwoWay compiled bindings subscribe to that event.
`IsReadOnly="True"` generates a `val` without a change event.

Default values are assigned inside the guarded `initializeComponent()` load,
before loading XBF. Like the upstream `InitializeXProperties`, this initializes
backing fields without raising change events. Repeated initialization preserves
the property's current value. Literals use registered application enum and
`CreateFromString` factories or the existing SDK conversion. Explicit complex defaults use the upstream
`XamlReader.Load` fragment path with the page's namespaces preserved.
