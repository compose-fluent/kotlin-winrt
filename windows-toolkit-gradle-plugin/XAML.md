# Kotlin XAML compilation

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

The build first analyzes declarations, then compiles isolated Kotlin semantic
symbols, generates XBF, and finally compiles application classes. Semantic
classes are never placed on the application's runtime classpath. XBF enters the
existing PRI staging pipeline with its relative resource path preserved.

The plugin pins the Windows x64 compiler release `0.1.0-preview.5`, protocol 2,
and archive SHA-256
`6f0d4e9738ed435edeb16519c15dc6472f9c83dcbc1cea0c3127c4986a3d69da`
from [the maintained fork](https://github.com/compose-fluent/microsoft-ui-xaml/releases/tag/kotlin-xamlc-v0.1.0-preview.5).
The first build downloads it; subsequent offline builds use a checksum-verified
Gradle user-home cache. GenXbf comes from the resolved WinUI NuGet package.
Applications do not need a compiler checkout or a stock XamlCompiler installed.

For fork development, `-PkotlinWinRT.xaml.compilerDirectory=<directory>` selects
an unpacked compiler and `-PkotlinWinRT.xaml.genXbfDirectory=<tools-directory>`
overrides GenXbf discovery. A custom release requires both
`kotlinWinRT.xaml.version` and `kotlinWinRT.xaml.sha256`. The `windows.xaml` DSL
also exposes these settings and `archiveUrl` for a custom distribution endpoint.

JVM and `mingwX64` use the same declaration and implementation contracts, with
separate semantic compilations and final outputs. The backend supports compiled
bindings, template scopes, phased bindings, converters, notifications and deferred
loading. Unsupported forms fail at build time; there is no runtime string-XAML
fallback. Target-specific execution and real UI acceptance are tracked separately
in `winui-gallery/XAML_MIGRATION.md`. IDE completion, navigation, designer and hot
reload integration remain deferred.

## Library inputs and resources

Ordinary Kotlin model libraries publish a XAML schema and compiled typed
accessors through the existing Kotlin/WinRT library identity. Inferred public
property types are exported by an isolated semantic compilation. These models
do not have to be authored Windows Runtime components. Consumer compilations
receive the schema and runtime registrar for both JVM and Native.

Classless XAML dictionaries can live in a library's resource source sets. They
compile to XBF and enter the existing AppX resource variants without generating
SDK projections or synthetic page connectors. Their relative resource paths are
preserved, so existing `ms-appx:///` dictionary URIs continue to resolve. NuGet
metadata remains a compiler input when projection generation is disabled.
Renaming or removing the last XAML input removes only this pipeline's stale
generated code and intermediate files; model-library schemas are preserved.
