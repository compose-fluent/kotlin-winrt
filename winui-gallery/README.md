# Kotlin WinUI Gallery

The Gallery uses adjacent XAML and Kotlin code-behind files. It preserves the
122 existing routes, including Home, and ports the static layouts, resources,
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
[`kotlin-xamlc-v0.1.0-preview.5`](https://github.com/compose-fluent/microsoft-ui-xaml/releases/tag/kotlin-xamlc-v0.1.0-preview.5),
protocol 2, from fork commit `d601a4c4dcf8c7ec3a0897f45bbb9570febe92b1`.
Its archive SHA-256 is
`6f0d4e9738ed435edeb16519c15dc6472f9c83dcbc1cea0c3127c4986a3d69da`.
Download and package checks run in Gradle, not the Kotlin compiler. GenXbf comes
from the selected Windows App SDK package. Building the application does not
require the XamlCompiler checkout or a local tool override.

The compiler supports named elements, ordinary and compiled-binding events,
compiled bindings, template scopes, collection notifications, converters,
phased bindings, deferred elements, application resources and custom Kotlin
XAML types. Unsupported forms produce build errors.

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
invocations with `--no-daemon`. The snapshot workflow gives each invocation an
8 GB heap with `-Dorg.gradle.jvmargs="-Xmx8g -XX:+UseSerialGC"`. Compiling the JVM
application concurrently with Native whole-program optimization can exhaust
that shared heap; use `--max-workers=1` for a local build of both targets.
Gradle compilation does not provide IDE completion or a XAML designer. The
repository does not yet ship an IDE plugin, designer or hot reload integration.
Completion and navigation have not been verified in an IDE. Community compiler
plugins are not loaded by default there, and IDE compiler versions require a
separate compatibility check; see the [Kotlin IDE integration documentation](https://kotlinlang.org/docs/custom-compiler-plugins.html#ide-integration).

Upstream licenses and asset attribution remain in
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
