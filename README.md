# kotlin-winrt

[![Publish Snapshot](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml/badge.svg?branch=master)](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml)
[![Snapshot](https://img.shields.io/maven-metadata/v?label=snapshot&metadataUrl=https%3A%2F%2Fcentral.sonatype.com%2Frepository%2Fmaven-snapshots%2Fio%2Fgithub%2Fcompose-fluent%2Fwinrt-runtime%2Fmaven-metadata.xml)](https://central.sonatype.com/repository/maven-snapshots/io/github/compose-fluent/winrt-runtime/maven-metadata.xml)
[![JVM](https://img.shields.io/badge/target-JVM%20%28JDK%2025%29-blue)](#targets)
[![mingwX64](https://img.shields.io/badge/target-mingwX64-green)](#targets)
[![Windows SDK](https://img.shields.io/badge/Windows%20SDK-10.0.26100.0-blue)](gradle.properties)
[![WindowsAppSDK](https://img.shields.io/badge/WindowsAppSDK-2.1.3-blue)](gradle.properties)

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
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3") {
        generateProjection = false
    }
}
```

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-app-sdk:2.1.3")
}

winRT {
    windowsSdk(includeExtensions = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3") {
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
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3") {
        generateProjection = true
    }

    type("Microsoft.UI.Xaml.Application")
    type("Microsoft.UI.Xaml.Window")
    type("Microsoft.UI.Xaml.Controls.Button")
}
```

Reusable WinRT libraries may declare `nugetPackage(...)` or `runtimeAsset(...)`. Final application modules consume dependency WinRT identity metadata and stage the aggregated runtime assets, so downstream apps do not need to repeat every library declaration just to place payloads in the final layout.

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
        mainClass.set("sample.MainKt")
        // console.set(true) enables a console window for diagnostics.
    }

    windowsSdk(includeExtensions = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3")
}
```

Run the JVM application through the generated host:

```powershell
.\gradlew.bat runWinRTApplicationHost
```

Run the native executable path:

```powershell
.\gradlew.bat runReleaseExecutableMingwX64
```

`runWinRTApplicationHost` and `runReleaseExecutableMingwX64` depend on the staging tasks automatically. They stage WinRT runtime assets, authored host DLLs, Windows App SDK payloads, and application layout files before launch. Do not wire `stageWinRTRuntimeAssets`, `buildWinRTAuthoringHost`, `buildWinRTApplicationHost`, or `stageWinRTApplicationPackage` manually for the normal run path.

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
.\gradlew.bat :winrt-samples:check
```

Useful focused sample runs:

```powershell
.\gradlew.bat :winrt-samples:runWinRTApplicationHost
.\gradlew.bat :winrt-samples:runReleaseExecutableMingwX64
.\gradlew.bat :winrt-samples:winui-kmp-app:runWinRTApplicationHost
.\gradlew.bat :winrt-samples:winui-kmp-app:runReleaseExecutableMingwX64
```

Linux and macOS can run static checks that do not require Windows Runtime execution:

```bash
./gradlew test
```

## Projection References

`kotlin-winrt` does not define WinRT projection behavior from scratch. Runtime classes, default interfaces, implemented interfaces, delegates, generic interfaces, parameterized IIDs, activation, authoring, and WinUI behavior are aligned with the Microsoft projection model:

- [C++/WinRT](https://github.com/microsoft/cppwinrt)
- [CsWinRT](https://github.com/microsoft/CsWinRT)

When local behavior disagrees with those projections, the intent is to change `kotlin-winrt` to match the reference projection model instead of inventing project-specific rules.
