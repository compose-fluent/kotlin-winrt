import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("io.github.composefluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
    .orElse("2.1.3")
val sampleWinUIEssentialVersion = providers.gradleProperty("kotlinWinRt.samples.winUIEssentialVersion")
    .orElse("1.6.7")
val sampleNuGetGlobalPackagesRoot = providers.gradleProperty("kotlinWinRt.samples.nugetGlobalPackagesRoot")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            executable()
        }
    }
    sourceSets {
        commonMain {
            dependencies {
                implementation(projects.winrtProjections)
            }
        }
        val selectedSampleMain = create(
            if (sampleWindowsAppSdkVersion.orNull != null) "winuiMain" else "noWinuiMain",
        ).apply {
            dependsOn(commonMain.get())
        }
        named("winuiJvmMain") {
            dependsOn(selectedSampleMain)
        }
        named("mingwX64Main") {
            dependsOn(selectedSampleMain)
        }
        named("winuiJvmTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

winRt {
    type("Windows.Foundation.IStringable")
    type("Windows.Foundation.Point")
    namespace("Windows.Data.Json")
    application {
        mainClass.set("io.github.composefluent.winrt.samples.MainKt")
    }
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        sampleNuGetGlobalPackagesRoot.orNull?.let { globalPackagesRoot ->
            nugetGlobalPackagesRoots.add(globalPackagesRoot)
            useNuGetCliGlobalPackages.set(false)
            restoreNuGetPackages.set(false)
        }
        windowsSdk(includeExtensions = true, generateProjection = true)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
            generateProjection = true
        }
        nugetPackage("WinUIEssential.WinUI3", sampleWinUIEssentialVersion.get())
        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.DependencyProperty")
        type("Microsoft.UI.Xaml.FrameworkElement")
        type("Microsoft.UI.Xaml.HorizontalAlignment")
        type("Microsoft.UI.Xaml.RoutedEventArgs")
        type("Microsoft.UI.Xaml.RoutedEventHandler")
        type("Microsoft.UI.Xaml.ResourceDictionary")
        type("Microsoft.UI.Xaml.Thickness")
        type("Microsoft.UI.Xaml.UIElement")
        type("Microsoft.UI.Xaml.VerticalAlignment")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.ComboBox")
        type("Microsoft.UI.Xaml.Controls.ComboBoxItem")
        type("Microsoft.UI.Xaml.Controls.ContentControl")
        type("Microsoft.UI.Xaml.Controls.ItemsControl")
        type("Microsoft.UI.Xaml.Controls.ListView")
        type("Microsoft.UI.Xaml.Controls.ListViewItem")
        type("Microsoft.UI.Xaml.Controls.MenuFlyout")
        type("Microsoft.UI.Xaml.Controls.MenuFlyoutItem")
        type("Microsoft.UI.Xaml.Controls.MenuFlyoutItemBase")
        type("Microsoft.UI.Xaml.Controls.Orientation")
        type("Microsoft.UI.Xaml.Controls.Page")
        type("Microsoft.UI.Xaml.Controls.Primitives.ButtonBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.FlyoutBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.RangeBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.Selector")
        type("Microsoft.UI.Xaml.Controls.Primitives.SelectorItem")
        type("Microsoft.UI.Xaml.Controls.Slider")
        type("Microsoft.UI.Xaml.Controls.StackPanel")
        type("Microsoft.UI.Xaml.Controls.TextBox")
        type("Microsoft.UI.Xaml.Controls.TextBlock")
        type("Microsoft.UI.Xaml.Controls.TextWrapping")
        type("Microsoft.UI.Xaml.Controls.ToggleSwitch")
        type("Microsoft.UI.Xaml.Controls.XamlControlsResources")
        type("Microsoft.UI.Xaml.Input.TappedEventHandler")
        type("Microsoft.UI.Xaml.Input.TappedRoutedEventArgs")
        type("Microsoft.UI.Xaml.Markup.XamlReader")
        type("Microsoft.UI.Xaml.Media.MicaBackdrop")
        type("Microsoft.UI.Xaml.Media.SystemBackdrop")
        type("WinUI3Package.SettingsCard")
        type("WinUI3Package.Shimmer")
        type("Windows.UI.Xaml.Interop.Type")
        type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
    }
}

val sampleJvmOptionProperties = listOf(
    "kotlin.winrt.samples.runNativeSmoke",
    "kotlin.winrt.samples.runComponentSmoke",
    "kotlin.winrt.samples.runWinUiSmoke",
    "kotlin.winrt.samples.autoNavigateWinUi",
    "kotlin.winrt.samples.autoShowMenuFlyout",
    "kotlin.winrt.samples.autoExitWinUi",
    "kotlin.winrt.samples.minimalWinUiSurface",
    "kotlin.winrt.samples.skipObjectContent",
    "kotlin.winrt.samples.skipSettingsCard",
    "kotlin.winrt.samples.skipShimmer",
    "kotlin.winrt.samples.enableShimmerLoading",
    "kotlin.winrt.samples.skipShimmerSizing",
    "kotlin.winrt.samples.skipMica",
    "kotlin.winrt.samples.noWinUiContent",
    "kotlin.winrt.samples.skipXamlResources",
    "kotlin.winrt.samples.skipWinUiResourceManager",
    "KOTLIN_WINRT_TRACE_CCW",
)

val standardSampleSmokeDefaults = mapOf(
    "kotlin.winrt.samples.runNativeSmoke" to "true",
    "kotlin.winrt.samples.runComponentSmoke" to "true",
    "kotlin.winrt.samples.runWinUiSmoke" to "true",
    "kotlin.winrt.samples.autoShowMenuFlyout" to "true",
    "kotlin.winrt.samples.autoExitWinUi" to "true",
)

tasks.named<io.github.composefluent.winrt.gradle.BuildWinRtApplicationHostTask>("buildWinRtApplicationHost") {
    val winuiJvmJar = tasks.named<Jar>("winuiJvmJar")
    val defaultJarName = providers.provider { "${project.name}-${project.version}.jar" }
    dependsOn(winuiJvmJar)
    runtimeClasspath.from(winuiJvmJar.flatMap { it.archiveFile })
    runtimeClasspath.from(
        providers.provider {
            configurations.named("winuiJvmRuntimeClasspath").get().filter { file ->
                file.name != defaultJarName.get()
            }
        },
    )
}

tasks.named<Exec>("runWinRtApplicationHost") {
    val hostJvmOptions = sampleJvmOptionProperties.joinToString(";") { name ->
        "-D$name=${providers.systemProperty(name).orElse(standardSampleSmokeDefaults[name] ?: "false").get()}"
    }
    environment("KOTLIN_WINRT_JVM_OPTIONS", hostJvmOptions)
}

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    dependsOn("stageWinRtRuntimeAssets")
    workingDir(projectDir)
    environment(
        "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
        layout.buildDirectory.dir("kotlin-winrt/runtime-assets").get().asFile.absolutePath,
    )
    sampleJvmOptionProperties.forEach { name ->
        providers.systemProperty(name).orElse(standardSampleSmokeDefaults[name] ?: "").orNull?.takeIf { it.isNotEmpty() }?.let { value ->
            environment(name, value)
        }
    }
}

val verifyWinRtSampleIdentity by tasks.registering {
    group = "verification"
    description = "Verifies the sample application aggregates Kotlin WinRT identity metadata from projection dependencies."
    val identityFile = layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json")
    val expectedWindowsAppSdkVersion = sampleWindowsAppSdkVersion.orNull
    dependsOn(tasks.named("generateWinRtApplicationIdentity"))
    inputs.file(identityFile)

    doLast {
        val identityJson = identityFile.get().asFile.readText()
        check("\"model\": \"application\"" in identityJson) {
            "Expected sample application identity JSON to use the application model."
        }
        check("winrt-projections" in identityJson) {
            "Expected sample application identity JSON to include the winrt-projections identity dependency."
        }
        check("winrt-runtime" !in identityJson) {
            "Runtime implementation dependencies must not be treated as Kotlin WinRT identity metadata."
        }
        if (expectedWindowsAppSdkVersion == null) {
            check("Microsoft.WindowsAppSDK" !in identityJson) {
                "WindowsAppSDK should only be declared when kotlinWinRt.samples.windowsAppSdkVersion is set."
            }
        } else {
            val expectedPackage = "Microsoft.WindowsAppSDK@$expectedWindowsAppSdkVersion"
            check(expectedPackage in identityJson) {
                "Expected sample application identity JSON to include $expectedPackage."
            }
        }
    }
}

val verifyWinRtSampleRuntimeAssets by tasks.registering {
    group = "verification"
    description = "Verifies the sample application stages local WinRT component runtime assets."
    val runtimeAssetsDir = layout.buildDirectory.dir("kotlin-winrt/runtime-assets")
    dependsOn(tasks.named("stageWinRtRuntimeAssets"))
    inputs.dir(runtimeAssetsDir)

    doLast {
        check(runtimeAssetsDir.get().asFile.resolve("SimpleMathComponent.dll").isFile) {
            "Expected SimpleMathComponent.dll to be staged as a local WinRT component runtime asset."
        }
    }
}

val verifyWinRtSampleRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the native Kotlin/WinRT host without opt-in native WinRT smoke tests."
    dependsOn(tasks.named("runWinRtApplicationHost"))
}

val verifyWinRtSampleMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the mingwX64 executable."
    dependsOn(tasks.named("runReleaseExecutableMingwX64"))
}

tasks.named("check") {
    dependsOn(verifyWinRtSampleIdentity)
    dependsOn(verifyWinRtSampleRuntimeAssets)
    dependsOn(verifyWinRtSampleRun)
    dependsOn(verifyWinRtSampleMingwRun)
}
