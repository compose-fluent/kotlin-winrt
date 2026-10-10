<h1><img src="docs/Assets/icon.svg" alt="kotlin-winrt logo" height="48" valign="middle"> Kotlin/WinRT</h1>

[![Release](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/release.yml/badge.svg)](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/release.yml)
[![Snapshot](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml/badge.svg?branch=master)](https://github.com/compose-fluent/kotlin-winrt/actions/workflows/publish-snapshot.yml)
[![JVM](https://img.shields.io/badge/JVM-JDK%2025-blue)](#requirements)
[![Native](https://img.shields.io/badge/Kotlin%2FNative-mingwX64-blue)](#requirements)

**Build Windows apps with Kotlin, WinRT and native WinUI 3 controls.**

Kotlin/WinRT projects Windows Runtime APIs into Kotlin and connects Kotlin code to the Windows ABI. Share application sources between Kotlin/JVM and Kotlin/Native `mingwX64`, write WinUI layouts in XAML with Kotlin code-behind, and package Windows applications through Gradle.

- **WinRT interop:** COM lifetime and identity, activation, strings, marshaling, delegates, generic interfaces, collections and async support for the implemented projection surface.
- **Metadata-driven APIs:** load WinMD from Windows SDK, NuGet or local files and generate Kotlin projections, or use prebuilt SDK projections.
- **Kotlin + XAML:** named controls, events, compiled bindings, resources, templates and authored Kotlin types, backed by XAML compilation and XBF/PRI assets.
- **Windows toolkit:** restore NuGet dependencies, stage native runtime files, build JVM launchers or Native executables, and create/sign MSIX packages.
- **Optional IDE integration:** project templates, Kotlin/XAML analysis, navigation, Gradle import, run/debug and JVM Hot Reload for supported IDE distributions.

The implementation follows Microsoft's [CsWinRT](https://github.com/microsoft/CsWinRT) and [C++/WinRT](https://github.com/microsoft/cppwinrt) projection model. Version 0.1.0 is the initial release; see [CHANGELOG.md](CHANGELOG.md) for its scope and limitations.

## See it running

The **Kotlin WinUI Gallery** shares XAML and Kotlin application code between JVM and Native. It contains 122 routes and 355 example documents, with interactive controls and a source viewer. Layouts and assets are ported from the official WinUI Gallery with [third-party attribution](winui-gallery/THIRD-PARTY-NOTICES.md).

![Kotlin WinUI Gallery home running on Windows](docs/Assets/winui-gallery.jpg)

![Button examples with XAML source in Kotlin WinUI Gallery](docs/Assets/winui-gallery-button.jpg)

Download from the [0.1.0 release](https://github.com/compose-fluent/kotlin-winrt/releases/tag/v0.1.0) after the tag's release workflow completes:

| Asset | Purpose |
| --- | --- |
| `Kotlin-WinUI-Gallery-0.1.0-jvm.msix` | Gallery with a bundled JVM runtime |
| `Kotlin-WinUI-Gallery-0.1.0-mingwX64.msix` | Native release build |
| `kotlin-winrt-ide-0.1.0-<variant>.zip` | IDE plugin; select the matching distribution below |
| `winui-gallery-signing.cer` | Public package signing certificate |
| `SHA256SUMS.txt` | Download checksums |

The Gallery packages share one application identity: install one at a time. They target Windows x64 and use Windows App SDK **2.5.1**. Framework-dependent installation requires the matching Windows App Runtime. If Windows does not already trust the signer, verify and trust the supplied public certificate before installing the MSIX. See the [Gallery guide](winui-gallery/README.md) for source builds.

## Requirements

- Windows x64. Sample manifests declare Windows 10 version 2004 (`10.0.19041.0`) as their minimum; individual Windows App SDK versions and APIs can require newer Windows releases.
- **JDK 25** for Gradle and JVM interop (`java.lang.foreign`). This repository uses Gradle **9.4.0**.
- Kotlin **2.4.0** or **2.4.20**; the toolkit selects the matching compiler plugin automatically.
- Visual Studio / Build Tools with **Desktop development with C++** and the selected Windows SDK (examples use **10.0.26100.0**). Toolchain discovery works from an ordinary terminal.
- Windows Developer Mode for packaged development runs. Creating an MSIX does not itself require Developer Mode.

The Gradle toolkit provisions its pinned WinApp CLI and Kotlin-aware XAML compiler automatically, and handles code generation, XAML compilation, packaging and running on both targets. Native builds use Kotlin/Native's `mingwX64` toolchain. See the [usage reference](docs/USAGE.md) for deployment modes, SDK selection and offline prerequisites.

## Get started

### Resolve the Gradle plugin from Maven Central

The Windows toolkit is **not published on Gradle Plugin Portal**. Add Maven Central to plugin repositories and explicitly map the plugin ID to its implementation artifact. Put this at the top of `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal() // Kotlin and other plugins
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "io.github.compose-fluent.windows-toolkit") {
                useModule(
                    "io.github.compose-fluent:windows-toolkit-gradle-plugin:${requested.version}"
                )
            }
        }
    }
}

dependencyResolutionManagement {
    repositories { mavenCentral() }
}

rootProject.name = "hello-winrt"
include(":app", ":winrt-projections")
```

### Start with Kotlin Multiplatform: mingwX64 + JVM

Start with **both `mingwX64` and JVM targets** and two modules: `app` for shared Kotlin/XAML application sources, and `winrt-projections` for SDK projections. The following Gradle configurations consume `0.1.0` from Maven Central.

Keep SDK generation in `winrt-projections/build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("io.github.compose-fluent.windows-toolkit") version "0.1.0"
}

kotlin {
    jvmToolchain(25)
    mingwX64()
    jvm { compilerOptions { freeCompilerArgs.add("-Xno-optimize") } }
}

windows {
    xaml { exportLibrarySchema = false }
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false, generateProjection = true)
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1")
        namespace("Microsoft.UI.Xaml")
        namespace("Microsoft.UI.Xaml.Controls")
        type("Microsoft.UI.Xaml.Media.MicaBackdrop")
        type("Windows.Foundation.Uri")
    }
}
```

Consume that projection from `app/build.gradle.kts` and keep the default packaged application mode:

```kotlin
plugins {
    kotlin("multiplatform") version "2.4.0"
    id("io.github.compose-fluent.windows-toolkit") version "0.1.0"
}

kotlin {
    jvmToolchain(25)
    mingwX64 {
        binaries { executable { entryPoint = "sample.hello.main" } }
    }
    jvm()
    sourceSets {
        getByName("winuiMain").dependencies {
            implementation(project(":winrt-projections"))
        }
    }
}

windows {
    application {
        mainClass = "sample.hello.MainKt"
        minWindowsVersion = "10.0.19041.0"
        launcherIcon = layout.projectDirectory.file(
            "src/winuiMain/appxResources/Assets/Application.ico"
        )
    }
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false)
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1") {
            generateProjection = false
        }
        // The shared projection module owns SDK declarations and additions.
        listOf("Windows", "Microsoft", "WinRT").forEach(::excludeAdditionNamespace)
    }
}
```

The toolkit adds the runtime and shared `winuiMain` source set. Add your `AppxManifest.xml` and referenced image assets under `app/src/winuiMain/appxResources/`, including the launcher icon configured above. The [Gallery manifest](winui-gallery/src/winuiMain/appxResources/AppxManifest.xml) provides a repository example; adapt its identity, display names and assets to your application.

**Use AppX development runs by default** (the `runAppx` development flow). In the current toolkit the concrete tasks are named `runWinAppPackage...`:

```powershell
.\gradlew.bat :app:runWinAppPackageMingwX64MainDebugExecutable --detach
.\gradlew.bat :app:runWinAppPackageJvmMain --detach
```

Run one target at a time. These tasks register a separate `.dev` package and launch with package identity, so `ms-appx:///` resources resolve correctly. Enable Windows Developer Mode. Development runs do not require creating or signing an MSIX. JVM and Native are equal application targets; choose JVM when using the current IDE Hot Reload transport. Direct host/executable launch is an advanced unpackaged workflow, not the default development path.

For snapshots, use `0.1.0-SNAPSHOT` and add this repository to **both** repository blocks above. Keep the same `resolutionStrategy`:

```kotlin
maven("https://central.sonatype.com/repository/maven-snapshots/") {
    mavenContent { snapshotsOnly() }
}
```

### Shared XAML and Kotlin code

Place XAML beside its Kotlin class: `MainWindow.xaml` and `MainWindow.kt` under `app/src/winuiMain/kotlin/sample/hello/`. Gradle compiles these sources for both targets.

```xml
<Window x:Class="sample.hello.MainWindow"
        xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
        xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml"
        Title="Hello Kotlin">
    <StackPanel Spacing="12" Padding="24">
        <TextBlock x:Name="Greeting" Text="Hello from Kotlin WinRT" />
        <Button Content="Say hello" Click="onHelloClick" />
    </StackPanel>
</Window>
```

```kotlin
package sample.hello

import microsoft.ui.xaml.RoutedEventArgs
import microsoft.ui.xaml.Window

class MainWindow : Window() {
    private fun onHelloClick(sender: Any?, args: RoutedEventArgs) {
        Greeting.text = "Hello from Kotlin code-behind"
    }
}
```

The compiler supplies typed named-element properties and initializes the component after construction. No `@XamlPage` annotation or handwritten generated superclass is needed. Add `App.xaml`, `App.kt` and `Main.kt` alongside the window:

```xml
<Application x:Class="sample.hello.App"
             xmlns="http://schemas.microsoft.com/winfx/2006/xaml/presentation"
             xmlns:x="http://schemas.microsoft.com/winfx/2006/xaml">
    <Application.Resources>
        <ResourceDictionary>
            <ResourceDictionary.MergedDictionaries>
                <XamlControlsResources xmlns="using:Microsoft.UI.Xaml.Controls" />
            </ResourceDictionary.MergedDictionaries>
        </ResourceDictionary>
    </Application.Resources>
</Application>
```

```kotlin
// App.kt
package sample.hello

import microsoft.ui.xaml.Application
import microsoft.ui.xaml.LaunchActivatedEventArgs

class App : Application() {
    private var window: MainWindow? = null

    override fun onLaunched(args: LaunchActivatedEventArgs) {
        window = MainWindow().also { it.activate() }
    }
}
```

```kotlin
// Main.kt
package sample.hello

import microsoft.ui.xaml.Application

private var application: App? = null

fun main() {
    Application.start { application = App() }
    application = null
}
```

Build a distributable Native package separately from development runs:

```powershell
.\gradlew.bat :app:packageWinAppMingwX64MainReleaseExecutable
```

Task suffixes follow the Kotlin target name. The Gallery uses `winuiJvm`, so its task is `:winui-gallery:runWinAppPackageWinuiJvmMain`. See [packaging and manual configuration](docs/USAGE.md#winui-applications).

### Optional IDE plugin

The IDE plugin improves completion, navigation, diagnostics, project creation and run/debug integration. Install the matching ZIP with **Settings → Plugins → Install Plugin from Disk**. Its **WinUI XAML Application** template creates the modules, sources, manifest, icons and bundled Maven toolchain described above; enable both targets.

| ZIP variant | IDE SDK used to build and validate it |
| --- | --- |
| `idea-262` | IntelliJ IDEA 2026.2.2 |
| `as-261` | Android Studio Quail 4 / 2026.1.4.7 |
| `as-canary-262` | Android Studio Rabbit 2 Canary 2 / 2026.2.2.2 |

Packages are built separately for each distribution; other IDE/compiler combinations are not covered by this compatibility claim. [IDE setup and limitations](winrt-compiler-plugin/ide/README.md).

**IDE limits:** XAML Preview is temporarily disabled. IDE Run/Debug and Hot Reload currently target JVM; Native Hot Reload transport is not implemented. Supported literal/resource/child edits can update live controls; other structural or Kotlin changes require rebuilding and restarting. See [Hot Reload](winrt-runtime/HOT_RELOAD.md).

## Prebuilt projections

Add prebuilt projections explicitly, or generate your application's namespaces. Prebuilt release versions follow the metadata baseline; runtime and toolkit versions are `0.1.0`:

```kotlin
dependencies {
    implementation("io.github.compose-fluent:winrt-projections-windows-sdk:10.0.26100.0")
    implementation("io.github.compose-fluent:winrt-projections-windows-app-sdk:2.2.0")
}

windows {
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", "2.2.0") {
            generateProjection = false
        }
    }
}
```

The prebuilt App SDK baseline is **2.2.0**; the Gallery and IDE templates generate **2.5.1** projections. Do not combine prebuilt `Windows.UI.Xaml` and Windows App SDK projection families in one consumer. See [projection selection](docs/USAGE.md#prebuilt-projections) for other baselines, WebView2 and snapshot coordinates.

## Build this repository

```powershell
git clone --recursive https://github.com/compose-fluent/kotlin-winrt.git
cd kotlin-winrt
.\gradlew.bat :winui-gallery:runWinAppPackageMingwX64MainDebugExecutable --args=Home --detach
.\gradlew.bat :winui-gallery:runWinAppPackageWinuiJvmMain --args=Home --detach
.\gradlew.bat :winrt-runtime:jvmTest :winrt-runtime:mingwX64Test
```

Runtime/ABI code lives in `winrt-runtime`; metadata and generation in `winrt-metadata` and `winrt-generator`; authoring in `winrt-authoring`; compiler and IDE support in `winrt-compiler-plugin`; Gradle integration in `windows-toolkit-gradle-plugin`. `winrt-projections`, `winrt-samples` and `winui-gallery` consume those layers.

Further reading: [usage reference](docs/USAGE.md) · [Gallery](winui-gallery/README.md) · [compiler](winrt-compiler-plugin/README.md) · [IDE](winrt-compiler-plugin/ide/README.md) · [release procedure](docs/RELEASING.md) · [benchmarks](winrt-benchmarks/README.md).
