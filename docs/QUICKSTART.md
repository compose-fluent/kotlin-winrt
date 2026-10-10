# WinUI quick start

Create a Windows application with shared Kotlin/XAML sources for **mingwX64 + JVM**, using Windows App SDK **2.5.1**. Prepare the [Windows build tools and JDK](../README.md#requirements), and use the Gradle **9.4.0** wrapper.

The project has this layout:

```text
hello-winrt/
  settings.gradle.kts
  app/
    build.gradle.kts
    src/winuiMain/
      appxResources/        # AppxManifest.xml and referenced icon assets
      kotlin/sample/hello/  # App, Main and MainWindow Kotlin/XAML files
  winrt-projections/
    build.gradle.kts
```

## 1. Resolve the Gradle plugin

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

## 2. Configure both targets

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

The toolkit adds the runtime and shared `winuiMain` source set. Add your `AppxManifest.xml` and referenced image assets under `app/src/winuiMain/appxResources/`, including the launcher icon configured above. The [Gallery manifest](../winui-gallery/src/winuiMain/appxResources/AppxManifest.xml) provides a repository example; adapt its identity, display names and assets to your application.

## 3. Add shared XAML and Kotlin sources

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

## 4. Run with AppX identity

**Use AppX development runs by default** (the `runAppx` development flow). In the current toolkit the concrete tasks are named `runWinAppPackage...`:

```powershell
.\gradlew.bat :app:runWinAppPackageMingwX64MainDebugExecutable --detach
.\gradlew.bat :app:runWinAppPackageJvmMain --detach
```

Run one target at a time. These tasks register a separate `.dev` package and launch with package identity, so `ms-appx:///` resources resolve correctly. Enable Windows Developer Mode. Development runs do not require creating or signing an MSIX. JVM and Native are equal application targets; choose JVM when using the current IDE Hot Reload transport. Direct host/executable launch is an advanced unpackaged workflow, not the default development path.

## Build an MSIX

Build a distributable Native package separately from development runs:

```powershell
.\gradlew.bat :app:packageWinAppMingwX64MainReleaseExecutable
```

Task suffixes follow the Kotlin target name. The Gallery uses `winuiJvm`, so its task is `:winui-gallery:runWinAppPackageWinuiJvmMain`. See [packaging and manual configuration](USAGE.md#winui-applications).

## Snapshot dependencies

For snapshots, use `0.1.0-SNAPSHOT` and add this repository to **both** repository blocks above. Keep the same `resolutionStrategy`:

```kotlin
maven("https://central.sonatype.com/repository/maven-snapshots/") {
    mavenContent { snapshotsOnly() }
}
```

