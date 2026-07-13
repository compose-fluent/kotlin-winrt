import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction

abstract class VerifyWinRTSampleModeTask : DefaultTask() {
    @get:Input
    abstract val winuiEnabled: Property<Boolean>

    @get:Input
    abstract val noWinuiMainPresent: Property<Boolean>

    @get:Input
    abstract val winuiJvmUsesNoWinui: Property<Boolean>

    @get:Input
    abstract val mingwUsesNoWinui: Property<Boolean>

    @get:Input
    abstract val winuiMainSourceDirectories: ListProperty<String>

    @get:Input
    abstract val configuredNuGetPackages: ListProperty<String>

    @TaskAction
    fun verify() {
        val enabled = winuiEnabled.get()
        val packages = configuredNuGetPackages.get().toSet()
        val hasWinuiUserSources = winuiMainSourceDirectories.get()
            .any { sourceDirectory -> sourceDirectory.endsWith("src/winuiMain/kotlin") }

        if (enabled) {
            check(!noWinuiMainPresent.get()) { "Default WinUI sample mode must not create noWinuiMain." }
            check(hasWinuiUserSources) { "Default WinUI sample mode must retain src/winuiMain/kotlin." }
            check("Microsoft.WindowsAppSDK" in packages) {
                "WinUI sample mode must configure Microsoft.WindowsAppSDK."
            }
            check("WinUIEssential.WinUI3" in packages) {
                "WinUI sample mode must configure WinUIEssential.WinUI3."
            }
        } else {
            check(noWinuiMainPresent.get()) { "Disabled WinUI sample mode must create noWinuiMain." }
            check(winuiJvmUsesNoWinui.get()) { "winuiJvmMain must depend on noWinuiMain." }
            check(mingwUsesNoWinui.get()) { "mingwX64Main must depend on noWinuiMain." }
            check(!hasWinuiUserSources) { "Disabled WinUI sample mode must exclude src/winuiMain/kotlin." }
            check("Microsoft.WindowsAppSDK" !in packages) {
                "Disabled WinUI sample mode must not configure Microsoft.WindowsAppSDK."
            }
            check("WinUIEssential.WinUI3" !in packages) {
                "Disabled WinUI sample mode must not configure WinUIEssential.WinUI3."
            }
        }
        println("KOTLIN_WINRT_SAMPLE_MODE=" + if (enabled) "winui" else "no-winui")
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("io.github.compose-fluent.winrt")
}

val sampleWinUIEnabled = providers.gradleProperty("kotlinWinRT.samples.enableWinUI")
    .map(String::toBooleanStrict)
    .orElse(true)
val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
    .orElse("2.1.3")
val sampleWinUIEssentialVersion = providers.gradleProperty("kotlinWinRT.samples.winUIEssentialVersion")
    .orElse("1.6.7")
val sampleNuGetGlobalPackagesRoot = providers.gradleProperty("kotlinWinRT.samples.nugetGlobalPackagesRoot")

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
        if (!sampleWinUIEnabled.get()) {
            named("winuiMain") {
                kotlin.setSrcDirs(emptyList<String>())
            }
            val noWinuiMain by creating {
                dependsOn(commonMain.get())
            }
            named("winuiJvmMain") {
                dependsOn(noWinuiMain)
            }
            named("mingwX64Main") {
                dependsOn(noWinuiMain)
            }
        }
        named("winuiJvmTest") {
            dependencies {
                implementation(libs.junit)
            }
        }
    }
}

winRT {
    application {
        mainClass.set("io.github.composefluent.winrt.samples.MainKt")
    }
    if (sampleWinUIEnabled.get()) {
        val windowsAppSdkVersion = sampleWindowsAppSdkVersion.get()
        type("Windows.Foundation.IStringable")
        type("Windows.Foundation.Point")
        namespace("Windows.Data.Json")
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

tasks.named<io.github.composefluent.winrt.gradle.RunWinRTApplicationHostTask>("runWinRTApplicationHost") {
    jvmArgs.addAll(
        providers.provider {
            sampleJvmOptionProperties.map { name ->
                "-D$name=${providers.systemProperty(name).orElse(standardSampleSmokeDefaults[name] ?: "false").get()}"
            }
        },
    )
}

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    dependsOn("stageWinRTApplicationPackage")
    workingDir(layout.buildDirectory.dir("kotlin-winrt/application-layout/mingwX64/release"))
    executable(layout.buildDirectory.file("kotlin-winrt/application-layout/mingwX64/release/${project.name}.exe").get().asFile.absolutePath)
    sampleJvmOptionProperties.forEach { name ->
        providers.systemProperty(name).orElse(standardSampleSmokeDefaults[name] ?: "").orNull?.takeIf { it.isNotEmpty() }?.let { value ->
            environment(name, value)
        }
    }
}

val verifyWinRTSampleIdentity by tasks.registering {
    group = "verification"
    description = "Verifies the sample application aggregates Kotlin WinRT identity metadata from projection dependencies."
    val identityFile = layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json")
    val expectedWindowsAppSdkVersion = sampleWindowsAppSdkVersion.get()
    dependsOn(tasks.named("generateWinRTApplicationIdentity"))
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
        if (!sampleWinUIEnabled.get()) {
            check("Microsoft.WindowsAppSDK" !in identityJson) {
                "WindowsAppSDK must not be declared when kotlinWinRT.samples.enableWinUI=false."
            }
        } else {
            val expectedPackage = "Microsoft.WindowsAppSDK@$expectedWindowsAppSdkVersion"
            check(expectedPackage in identityJson) {
                "Expected sample application identity JSON to include $expectedPackage."
            }
        }
    }
}

val verifyWinRTSampleMode by tasks.registering(VerifyWinRTSampleModeTask::class) {
    group = "verification"
    description = "Verifies explicit WinUI/no-WinUI sample source-set and NuGet selection."
    val noWinuiMain = kotlin.sourceSets.findByName("noWinuiMain")
    val winuiMain = kotlin.sourceSets.getByName("winuiMain")
    val winuiJvmMain = kotlin.sourceSets.getByName("winuiJvmMain")
    val mingwX64Main = kotlin.sourceSets.getByName("mingwX64Main")
    val packages = project.extensions
        .getByType<io.github.composefluent.winrt.gradle.WinRTExtension>()
        .nugetPackages
        .map { pkg -> pkg.packageId }

    winuiEnabled.set(sampleWinUIEnabled)
    noWinuiMainPresent.set(noWinuiMain != null)
    winuiJvmUsesNoWinui.set(noWinuiMain != null && noWinuiMain in winuiJvmMain.dependsOn)
    mingwUsesNoWinui.set(noWinuiMain != null && noWinuiMain in mingwX64Main.dependsOn)
    winuiMainSourceDirectories.set(winuiMain.kotlin.srcDirs.map { sourceDir -> sourceDir.invariantSeparatorsPath })
    configuredNuGetPackages.set(packages)
}

val verifyWinRTSampleRuntimeAssets by tasks.registering {
    group = "verification"
    description = "Verifies the sample application stages local WinRT component runtime assets."
    val runtimeAssetsDir = layout.buildDirectory.dir("kotlin-winrt/runtime-assets")
    dependsOn(tasks.named("stageWinRTRuntimeAssets"))
    inputs.dir(runtimeAssetsDir)

    doLast {
        check(runtimeAssetsDir.get().asFile.resolve("SimpleMathComponent.dll").isFile) {
            "Expected SimpleMathComponent.dll to be staged as a local WinRT component runtime asset."
        }
    }
}

val verifyWinRTSampleRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the native Kotlin/WinRT host without opt-in native WinRT smoke tests."
    dependsOn(tasks.named("runWinRTApplicationHost"))
}

val verifyWinRTSampleMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application through the mingwX64 executable."
    dependsOn(tasks.named("runReleaseExecutableMingwX64"))
}

tasks.named("check") {
    dependsOn(verifyWinRTSampleMode)
    dependsOn(verifyWinRTSampleIdentity)
    dependsOn(verifyWinRTSampleRuntimeAssets)
    dependsOn(verifyWinRTSampleRun)
    dependsOn(verifyWinRTSampleMingwRun)
}
