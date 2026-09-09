# kotlin-winrt

[![Publish Snapshot](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml/badge.svg?branch=master)](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml)
[![Snapshot](https://img.shields.io/maven-metadata/v?label=snapshot&metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fgithub%2Fcompose-fluent%2Fwinrt-runtime%2Fmaven-metadata.xml)](https://central.sonatype.com/repository/maven-snapshots/io/github/compose-fluent/winrt-runtime/maven-metadata.xml)
[![JVM](https://img.shields.io/badge/target-JVM%20%28JDK%2025%29-blue)](#targets)
[![mingwX64](https://img.shields.io/badge/target-mingwX64-green)](#targets)
[![Windows SDK](https://img.shields.io/badge/Windows%20SDK-10.0.26100.0-blue)](gradle.properties)
[![WindowsAppSDK](https://img.shields.io/badge/WindowsAppSDK-2.2.0-blue)](gradle.properties)

`kotlin-winrt` is a Kotlin projection for WinRT and WinUI 3. It provides runtime interop, WinMD metadata loading, projection generation, Kotlin-authored WinRT type support, runtime asset staging, and WinUI application launch support for:

- Kotlin/JVM on Windows
- Kotlin/Native `mingwX64` on Windows

The implementation is reference-first: `.cswinrt/` is the local engineering baseline for runtime behavior, generated surface shape, authoring contracts, packaging evidence, and validation.

## Targets

Current supported validation targets are:

- JVM: uses JDK 25 and the `java.lang.foreign` FFM API for the Win32/COM bridge.
- `mingwX64`: supports runtime calls, generated projections, native executables, and native authored WinRT component exports for the implemented surface.

WinUI validation runs through both the generated JVM application host and the `mingwX64` executable path.

## Modules

- `winrt-runtime`: WinRT ABI, COM interop, activation, marshaling, object identity, WinUI bootstrap, and runtime helpers.
- `winrt-metadata`: WinMD loading and normalized metadata model construction.
- `winrt-generator`: Kotlin source generation for WinRT and WinUI projection bindings.
- `winrt-compiler-plugin`: compiler-visible projection and authoring support.
- `winrt-projections`: generated projection output and prebuilt Windows SDK / Windows.UI.Xaml / Windows App SDK projection artifacts.
- `winrt-authoring`: Kotlin-authored WinRT type, TypeDetails, host manifest, and native export support.
- `winrt-benchmarks`: projection-cost comparisons across Kotlin/JVM, Kotlin/Native, CsWinRT, and C++/WinRT.
- `winrt-samples`: validation applications and sample surfaces.

## Snapshot Setup

Snapshot artifacts are published under the Maven group `io.github.compose-fluent`. Add the Sonatype snapshot repository only for snapshot versions:

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            name = "mavenCentralSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            name = "mavenCentralSnapshots"
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent {
                snapshotsOnly()
            }
        }
    }
}
```

For dependency repositories in an existing build, the required repository block is:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "mavenCentralSnapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent {
            snapshotsOnly()
        }
    }
}
```

Apply the Gradle plugin in a JVM or KMP module:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.compose-fluent.winrt") version "0.1.0-SNAPSHOT"
}
```

The plugin adds `winrt-runtime` automatically to JVM and KMP main configurations. Add runtime dependencies manually only when using the runtime without the plugin:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-runtime-jvm:0.1.0-SNAPSHOT")
}
```

For KMP without the plugin:

```kotlin
kotlin {
    jvm()
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation("io.github.compose-fluent:winrt-runtime:0.1.0-SNAPSHOT")
        }
    }
}
```

## Projecting WinRT APIs

For a JVM-only project:

```kotlin
plugins {
    kotlin("jvm")
    id("io.github.compose-fluent.winrt") version "0.1.0-SNAPSHOT"
}

winRT {
    windowsSdk(generateProjection = true)
    namespace("Windows.Data.Json")
}
```

For a JVM + `mingwX64` project:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.compose-fluent.winrt") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvm()
    mingwX64 {
        binaries {
            executable()
        }
    }
}

winRT {
    windowsSdk(generateProjection = true)
    namespace("Windows.Data.Json")
}
```

Generated projection types can be used from ordinary Kotlin code. Non-XAML WinRT calls should run inside a runtime scope:

```kotlin
import io.github.composefluent.winrt.runtime.RuntimeScope
import windows.`data`.json.JsonArray
import windows.`data`.json.JsonObject

fun readProfile(json: String): String =
    RuntimeScope.initializeSingleThreaded().use {
        val profile = JsonObject.parse(json)
        val education = profile.getNamedArray("education", JsonArray())
        val firstSchool = education.getObjectAt(0u).getNamedObject("school")

        "${profile.getNamedString("name")} attended ${firstSchool.getNamedString("name")}"
    }
```

## Prebuilt Projections

For the default Windows SDK and one XAML family, prefer the prebuilt projection artifacts. Applications select the Windows SDK coordinate independently, then choose either the legacy `Windows.UI.Xaml` family or the Windows App SDK / WinUI family:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-ui-xaml:10.0.26100.0")
}

winRT {
    windowsSdk(includeExtensions = true)
}
```

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-app-sdk:2.2.0")
}

winRT {
    windowsSdk(includeExtensions = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.2.0") {
        generateProjection = false
    }
}
```

Do not consume `winrt-projections-windows-ui-xaml` and `winrt-projections-windows-app-sdk` together. The App SDK artifact exposes WebView2 Core transitively; add `winrt-projections-windows-webview2` directly only when using the standalone WebView2 projection without the App SDK family.

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-webview2:1.0.3719.77")
}
```

Projection artifact versions follow their metadata baseline. Snapshot projection builds append the kotlin-winrt snapshot suffix, for example `10.0.26100.0-kotlin-winrt-0.1.0-SNAPSHOT`.

The WebView2 Core projection is a separate artifact, matching the `Microsoft.Web.WebView2` NuGet boundary. The Windows App SDK projection depends on it publicly and owns the WinUI `Microsoft.UI.Xaml.Controls.WebView2` control, so applications using the App SDK coordinate receive both surfaces without duplicate generated classes.

Prebuilt projections are never selected implicitly from a Windows SDK or NuGet version declaration. Add the prebuilt projection artifact to `dependencies` when you want to use it. A `nugetPackage(...)` declaration defaults to local projection generation; set `generateProjection = false` only when the projection surface is supplied by an explicit dependency and the NuGet package is needed for runtime assets.

When a project intentionally needs a local projection from a NuGet package, keep projection generation enabled:

```kotlin
winRT {
    windowsSdk(includeExtensions = true, generateProjection = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.2.0") {
        generateProjection = true
    }

    type("Microsoft.UI.Xaml.Application")
    type("Microsoft.UI.Xaml.Window")
    type("Microsoft.UI.Xaml.Controls.Button")
}
```

Reusable WinRT libraries may declare `nugetPackage(...)` or `runtimeAsset(...)`. Final application modules consume dependency WinRT identity metadata and stage the aggregated runtime assets, so downstream apps do not need to repeat every library declaration just to place payloads in the final layout.

## WinApp CLI and NuGet Restore

The Gradle plugin translates project and dependency `nugetPackage(...)` declarations into an internal `build/generated/kotlin-winrt/winapp/winapp.yaml`. Do not create or maintain that file manually. With the default `restoreNuGetPackages = true`, `restoreWinAppDependencies` runs `winapp restore`, validates its schema-3 lockfile, and uses the resolved WinMD files for projection generation. The same lockfile and `.winapp/bin/<architecture>` output drive DLL, PRI, asset, and manifest staging after compilation; explicit local `winmd(...)` inputs remain part of the projection input set.

WinApp CLI provisioning is automatic and does not use WinGet, modify `PATH`, or require an administrator install. The plugin first probes `winAppCliExecutable` (default: `winapp`) and uses it only when it reports the required version, currently `0.6.0`. Otherwise it downloads `Microsoft.Windows.SDK.BuildTools.WinApp` from NuGet, verifies the pinned SHA-512 checksum, and caches the extracted host tools below the Gradle user home. A custom system location can be selected without changing `PATH`:

```kotlin
winRT {
    winAppCliExecutable = "C:/tools/winapp.exe"
}
```

The plugin's `winapp restore`, `winapp package`, and `winapp tool makeappx` paths do not invoke MSBuild and do not require an MSBuild project. JVM and `mingwX64` compilation still require their normal JDK, Kotlin/Native, C/C++, and Windows SDK prerequisites.

JVM native EXE and authoring DLL hosts automatically discover installed Visual Studio or Build Tools C++ toolchains through `vswhere`, including custom installation locations. The plugin initializes the MSVC environment for the variant's architecture and the configured Windows SDK in a child process; builds work from an ordinary terminal or IDE without a Developer Command Prompt or system `PATH` changes. An already configured matching C++ environment and `clang-cl` on `PATH` remain supported. Missing C++ components must be installed through Visual Studio Installer; the plugin does not install MSVC or invoke MSBuild to compile these hosts.

WinApp CLI `0.6.0` performs its normal C++/WinRT workspace setup during restore and may check or install Windows App SDK runtime packages. That release does not expose a switch that limits `restore` to NuGet download and lockfile generation, and WinApp itself has no `--offline` option. When Gradle runs with `--offline`, the plugin does not invoke `winapp restore`: it reuses the verified `.winapp` lock/cache and fails if the lock, package contents, or restore context is missing or stale. Without Gradle offline, a normal restore may use the configured NuGet sources and CLI behavior. Set `restoreNuGetPackages = false` to retain the legacy NuGet cache/CLI resolution path.

For the default unpackaged application mode, the plugin keeps a loose staged layout for each generated JVM host or `mingwX64` executable. With `application { packaged() }`, it creates one package task graph per matching Kotlin target variant. The unsuffixed `packageWinRTApplication` task aggregates those concrete tasks. A `mingwX64` executable remains its variant's package entry payload; JVM variants package the generated host, runtime classpath, and the same staged WinRT resources. `.msix` outputs use `winapp package`, while an explicitly configured `.appx` output uses `winapp tool makeappx pack`. Each concrete `verifyWinRTApplicationPackage<TargetVariant>` task unpacks its result through `winapp tool makeappx` and validates its manifest, payload, and resource-resolution report. Existing builds can keep an explicit Windows SDK MakeAppx path as a legacy override:

Application package files live under the owning Kotlin source set at `src/<targetSourceSet>/appxResources/` (for example `src/winuiMain/appxResources/` or `src/main/appxResources/`). The plugin copies every file below that directory into the staged AppX root using its path relative to `appxResources/`; `AppxManifest.xml` is used automatically when no `application { appxManifest(...) }` is configured and is not copied as a second payload file. Source-set dependencies are merged from the least-specific source set to the selected target, so a target resource can override a shared resource. Explicit `appxManifest(...)` manifests take precedence. Explicit `packagePayload(...)` entries have the highest normal resource priority; same-level conflicts fail instead of being chosen by directory traversal order.

Windows version settings belong to Gradle, not a second copy in the source manifest:

```kotlin
winRT {
    windowsSdk("10.0.26100.0")
    application {
        minWindowsVersion = "10.0.19041.0"
        // maxVersionTested defaults to the selected SDK; override it only for a different tested OS.
    }
}
```

Keep `<TargetDeviceFamily Name="Windows.Desktop" />` in the source manifest without version attributes. Staging writes `MinVersion` and `MaxVersionTested` into the copied manifest; conflicting source attributes fail. When no target family is declared, staging adds `Windows.Desktop`. This applies to normal and `.dev` layouts, including manifests in unpackaged layouts, and never edits the source file. `minWindowsVersion` is required whenever an AppX manifest is staged and must not exceed `maxVersionTested`. Named applications inherit both settings and may override them independently.

`windowsSdk(version)` selects the Windows API baseline and installed native SDK. When omitted, the plugin uses one shared, configuration-cache-tracked installed SDK selection for metadata, native tools, and the default `maxVersionTested`; pin the version for reproducible builds. `MaxVersionTested` is not an upper installation limit, and using a newer SDK does not make new APIs available on older Windows versions. Guard those calls when supporting an older `minWindowsVersion`.

The NuGet toolchain revision is separate: `windowsSdkToolsVersion = "10.0.26100.4654"` selects the exact `Microsoft.Windows.SDK.CPP` and `Microsoft.Windows.SDK.BuildTools` packages used by WinApp. Its default remains `10.0.26100.1742`. Changing this revision does not change the API baseline or manifest OS versions; the plugin does not invent a NuGet package version from an OS version. Explicit declarations of those tool packages must match the configured toolchain revision.

When a source set contains AppX resources, the plugin generates a KotlinPoet `AppxRes` accessor in that module's configured resource package. For example, `AppxRes.Assets.Square44x44LogoPng.path` is the package-root-relative path and `.uri` lazily creates the corresponding `ms-appx:///` `Windows.Foundation.Uri`; the accessor follows the shared Windows source-set visibility of the module.

Kotlin Multiplatform applications expose one task graph for each JVM main compilation and every declared MinGW executable build variant. For example, a `winuiJvm` main compilation plus the default MinGW executables creates `packageWinRTApplicationWinuiJvmMain`, `packageWinRTApplicationMingwX64MainDebugExecutable`, and `packageWinRTApplicationMingwX64MainReleaseExecutable`. The same suffix is used by staging, run, verification, signing, and installation tasks, and every variant has isolated layouts, package files, reports, and verification directories. The application DSL has no global target or build-type selectors; choose the artifact by invoking its concrete task.

JVM distribution and Windows App SDK deployment are independent settings. The default `bundledJvmRuntime()` creates or copies a runtime image beside the host; `externalJvmRuntime("C:/path/to/jdk")` requires that JVM on the target machine. `frameworkDependent()` keeps restored Windows App SDK framework packages as manifest dependencies, while `selfContained()` stages the supported `runtimes-framework` payload in the application layout. These settings do not change `packaged()` versus `unpackaged()`.

NuGet source configuration follows NuGet's normal directory hierarchy. `winRT { nugetConfig("path/to/NuGet.Config") }` selects an explicit config, while `nugetConfigDirectory` can select the restore base directory. The plugin still generates `winapp.yaml`; users do not maintain that file or a second global cache. In offline mode, restore only reuses a verified lock/cache and fails clearly when a package or lock entry is missing.

```kotlin
winRT {
    application {
        packaged()
        minWindowsVersion = "10.0.19041.0"
        makeAppxExecutable = "C:/Program Files (x86)/Windows Kits/10/bin/10.0.26100.0/x64/makeappx.exe"
    }
}
```

## WinUI Applications

Enable the application model only in the final executable app module:

```kotlin
plugins {
    kotlin("multiplatform")
    id("io.github.compose-fluent.winrt") version "0.1.0-SNAPSHOT"
}

kotlin {
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            executable()
        }
    }
}

winRT {
    application {
        mainClass = "sample.MainKt"
        minWindowsVersion = "10.0.19041.0"
        // console = true enables a console window for diagnostics.
    }

    windowsSdk(includeExtensions = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.2.0")
}
```

The dual-target project exposes separate JVM and Native task graphs automatically. Run the JVM host with:

```text
./gradlew runWinRTApplicationHostWinuiJvmMain
```

Run a MinGW build through its Kotlin/Native executable task, for example:

```text
./gradlew runReleaseExecutableMingwX64
```

Use a concrete packaging task when building one artifact, or the unsuffixed aggregate to build all artifacts. For example, `packageWinRTApplicationMingwX64MainReleaseExecutable` builds only the release Native package, while `packageWinRTApplication` builds the JVM, debug Native, and release Native packages. Targets and executable build types are declared once in the Kotlin DSL; packaging follows that model without separate selection properties.

To build several applications in one invocation, declare named variants in the first `application` block. Settings outside `variants` are shared defaults; each application can override them:

```kotlin
winRT {
    application {
        mainClass = "sample.MainKt"
        packaged()
        variants {
            create("desktop") {
                variantName = "winuiJvm:main"
                runTask("runDesktopDiagnostics") {
                    jvmArgs.add("-Xmx256m")
                }
            }
            create("native") {
                variantName = "mingwX64:main:releaseExecutable"
            }
        }
    }
}
```

This registers `packageWinRTApplicationDesktop` and `packageWinRTApplicationNative`, with matching suffixed staging, verification, signing, and installation tasks. Each named application requires a full `variantName` (`target:compilation` for JVM, `target:compilation:executable` for Native); this binding is only available inside named variants. The unsuffixed tasks aggregate all named applications. Layouts, identity files, PRI work directories, generated Native entries, packages, and verification reports are isolated per application. Native applications must bind different executable binaries, even when they share a compilation. Explicit package output paths must also be distinct.

For multiple JVM applications, use the same JVM target with different main classes or explicitly bind a custom compilation (`variantName = "winuiJvm:preview"`). Kotlin Gradle Plugin 2.3.20 rejects compiling multiple JVM targets in one project.

Use `runWinRTApplicationHostDesktop` for a JVM variant's direct host launch and the selected Kotlin/Native executable's run task for Native. These direct launches do not activate a packaged application's identity; use the packaged development run tasks below or install and activate an MSIX for packaged startup verification. Named applications do not inject all their layouts into the global Java `processResources`, `JavaExec`, or distribution tasks. Register additional JVM runs inside the corresponding variant with `runTask`.

For packaged development, configure `application { packaged() }` and invoke the concrete package run task:

```powershell
.\gradlew.bat runWinRTApplicationPackageWinuiJvmMain
.\gradlew.bat runWinRTApplicationPackageMingwX64MainDebugExecutable
```

These tasks build their own variant, stage a development manifest with `.dev` appended to `Identity.Name`, regenerate the application PRI with that development identity, and pass the layout to `winapp run` in folder mode. The source manifest and normal package outputs remain unchanged. WinApp creates a separate development AppX layout, registers it, and launches through package identity, so `ms-appx:///` resources use the registered layout. This does not require creating or signing an MSIX, setting `installPackage`, or invoking MSBuild. Windows Developer Mode must be enabled. WinApp preserves development application data across redeployments by default. Each variant has an isolated deployment directory; variants with the same source manifest identity replace the same `.dev` registration, so packaged run tasks have no unsuffixed aggregate.

The `.dev` identity coexists with the installed production application and has separate application data. The source identity must leave four characters available within Windows' 50-character identity-name limit. If the input already has `resources.pri`, development staging requires `generateProjectPri` and its resource inputs to rebuild it; the plugin will not reuse a PRI indexed under the production identity. WinApp still refuses to overwrite any non-development package already occupying the `.dev` identity. The plugin never removes installed production packages automatically.

Packaged development runs currently require `frameworkDependent()` (the default). WinApp CLI 0.6 has no self-contained folder-run option and would add Windows App SDK framework dependencies, so `selfContained()` is rejected rather than silently changing the deployment model. Install and activate the generated MSIX to verify self-contained deployment.

The task waits for the application to exit and streams WinApp output to Gradle. Use `--detach` to return after launch, `--args='--flag value'` to pass an application command line, `--debug-output` to capture native debug output and exceptions, or `--no-launch` to register without launching. Native debug capture occupies the process's debugger connection; leave it off when using another native debugger. Named applications expose the same task with their name, for example `runWinRTApplicationPackageDesktop`.

Run the JVM application through the generated host:

```powershell
.\gradlew.bat runWinRTApplicationHostWinuiJvmMain
```

Run the native executable path:

```powershell
.\gradlew.bat runReleaseExecutableMingwX64
```

`runWinRTApplicationHostWinuiJvmMain` and `runReleaseExecutableMingwX64` depend on their variant-specific staging tasks automatically. They stage WinRT runtime assets, authored host DLLs, Windows App SDK payloads, and application layout files before launch. The unsuffixed WinRT application tasks are aggregate entry points; use a suffixed task when operating on one target variant. Do not wire staging or host tasks manually for the normal run path.

A minimal WinUI entry point starts XAML directly:

```kotlin
import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs
import microsoft.ui.xaml.Thickness
import microsoft.ui.xaml.Window
import microsoft.ui.xaml.controls.Button
import microsoft.ui.xaml.controls.StackPanel
import microsoft.ui.xaml.controls.TextBlock
import microsoft.ui.xaml.controls.XamlControlsResources

fun main() {
    Application.start {
        DemoApp()
    }
}

class DemoApp : Application() {
    private var window: Window? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        Application.current.resources.mergedDictionaries.add(XamlControlsResources())

        val root = StackPanel().apply {
            padding = Thickness(24.0, 24.0, 24.0, 24.0)
            spacing = 12.0
            children.add(TextBlock().apply {
                text = "Hello from Kotlin WinRT"
                fontSize = 24.0
            })
            children.add(Button().apply {
                content = "OK"
            })
        }

        window = Window().apply {
            title = "Kotlin WinRT"
            content = root
            activate()
        }
    }
}
```

Do not wrap `Application.start` in `RuntimeScope.initializeSingleThreaded()`. XAML application startup owns its WinRT module lifetime. `RuntimeScope` remains the normal scope for non-XAML WinRT API calls.

If you use a custom launcher or a Gradle `JavaExec` task instead of `runWinRTApplicationHost`, create the same application host scope before `Application.start`:

```kotlin
import io.github.composefluent.winrt.runtime.WinRTWindowsAppSdkBootstrap
import microsoft.ui.xaml.Application

fun main() {
    WinRTWindowsAppSdkBootstrap.initializeApplicationHost().use {
        Application.start {
            DemoApp()
        }
    }
}
```

For packaged custom launchers, pass `unpackaged = false`; the generated hosts do this from `winRT { application { packageMode } }`.

When `winRT { application {} }` is enabled, the plugin wires unpackaged `JavaExec` tasks to the staged payload and passes `-Dkotlin.winrt.runtimeAssetsRoot=...`. Custom native launchers or external packaging tools still need to place the staged `kotlin-winrt-runtime-assets` directory beside the launcher or pass `-Dkotlin.winrt.runtimeAssetsRoot=<path>`.

## Validation

Use Windows for full build and runtime validation:

```powershell
.\gradlew.bat test
.\gradlew.bat validateWinRTMingwParity
.\gradlew.bat validateWinRTFullWindowsSdkProjectionGate
.\gradlew.bat validateWinRTSampleSmoke
```

The sample modules' normal `check` tasks stay focused on compilation and unit tests. `validateWinRTSampleSmoke` runs the machine-dependent JVM/native launches and generated-output audits.

Run the projection benchmark matrix on an x64 Windows machine with .NET 8 and the Visual Studio C++ build tools:

```powershell
.\gradlew.bat :winrt-benchmarks:benchmarkAll
```

The generated comparison report is written to `winrt-benchmarks/build/reports/benchmarks/benchmark-report.md`. See `winrt-benchmarks/README.md` for workload semantics, runner prerequisites, and tuning parameters.

Useful focused sample runs:

```powershell
.\gradlew.bat :winrt-samples:runWinRTApplicationHostWinuiJvmMain
.\gradlew.bat :winrt-samples:runReleaseExecutableMingwX64
.\gradlew.bat :winrt-samples:winui-kmp-app:runWinRTApplicationHostWinuiJvmMain
.\gradlew.bat :winrt-samples:winui-kmp-app:runReleaseExecutableMingwX64
```

Run the WebView2 sample with an installed Evergreen WebView2 Runtime:

```powershell
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=false" :winrt-samples:runWinRTApplicationHostWinuiJvmMain
& .\gradlew.bat "-Dkotlin.winrt.samples.runWebView2Sample=true" "-Dkotlin.winrt.samples.autoExitWinUi=false" :winrt-samples:runReleaseExecutableMingwX64
```

The sample starts with embedded offline HTML and accepts HTTP or HTTPS addresses from its navigation bar. Both launch paths stage `WebView2Loader.dll` and the Windows App SDK payload through the normal application layout tasks, and keep WebView2 user data under `winrt-samples/build/kotlin-winrt/webview2-user-data` instead of the tracked application layout.

Linux and macOS can run static checks that do not require Windows Runtime execution:

```bash
./gradlew test
```

## Projection References

`kotlin-winrt` does not define WinRT projection behavior from scratch. Runtime classes, default interfaces, implemented interfaces, delegates, generic interfaces, parameterized IIDs, activation, authoring, and WinUI behavior are aligned with the Microsoft projection model:

- [C++/WinRT](https://github.com/microsoft/cppwinrt)
- [CsWinRT](https://github.com/microsoft/CsWinRT)

When local behavior disagrees with those projections, the intent is to change `kotlin-winrt` to match the reference projection model instead of inventing project-specific rules.
