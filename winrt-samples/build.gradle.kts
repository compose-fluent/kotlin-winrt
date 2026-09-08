import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.Input
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
    .orElse("2.2.0")
val sampleWinUIEssentialVersion = providers.gradleProperty("kotlinWinRT.samples.winUIEssentialVersion")
    .orElse("1.6.7")
val sampleNuGetGlobalPackagesRoot = providers.gradleProperty("kotlinWinRT.samples.nugetGlobalPackagesRoot")
val sampleApplicationPackageMode = providers.gradleProperty("kotlinWinRT.samples.packageMode")
    .map(String::lowercase)
    .orElse("unpackaged")

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
        mainClass = "io.github.composefluent.winrt.samples.MainKt"
        when (sampleApplicationPackageMode.get()) {
            "packaged" -> packaged()
            "unpackaged" -> unpackaged()
            else -> error(
                "kotlinWinRT.samples.packageMode must be 'packaged' or 'unpackaged', " +
                    "but was '${sampleApplicationPackageMode.get()}'",
            )
        }
    }
    if (sampleWinUIEnabled.get()) {
        val windowsAppSdkVersion = sampleWindowsAppSdkVersion.get()
        type("Windows.Foundation.IStringable")
        type("Windows.Foundation.Point")
        namespace("Windows.Data.Json")
        sampleNuGetGlobalPackagesRoot.orNull?.let { globalPackagesRoot ->
            nugetGlobalPackagesRoots.add(globalPackagesRoot)
            useNuGetCliGlobalPackages = false
            restoreNuGetPackages = false
        }
        windowsSdk(includeExtensions = true, generateProjection = true)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
            generateProjection = true
        }
        nugetPackage("WinUIEssential.WinUI3", sampleWinUIEssentialVersion.get())
        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.DependencyProperty")
        type("Microsoft.UI.Xaml.FrameworkElement")
        type("Microsoft.UI.Xaml.GridLength")
        type("Microsoft.UI.Xaml.GridUnitType")
        type("Microsoft.UI.Xaml.HorizontalAlignment")
        type("Microsoft.UI.Xaml.RoutedEventArgs")
        type("Microsoft.UI.Xaml.RoutedEventHandler")
        type("Microsoft.UI.Xaml.ResourceDictionary")
        type("Microsoft.UI.Xaml.Thickness")
        type("Microsoft.UI.Xaml.UIElement")
        type("Microsoft.UI.Xaml.VerticalAlignment")
        type("Microsoft.UI.Xaml.Visibility")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.Automation.AutomationProperties")
        type("Microsoft.UI.Xaml.Controls.Border")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.ColumnDefinition")
        type("Microsoft.UI.Xaml.Controls.ComboBox")
        type("Microsoft.UI.Xaml.Controls.ComboBoxItem")
        type("Microsoft.UI.Xaml.Controls.ContentControl")
        type("Microsoft.UI.Xaml.Controls.Grid")
        type("Microsoft.UI.Xaml.Controls.ItemsControl")
        type("Microsoft.UI.Xaml.Controls.ListView")
        type("Microsoft.UI.Xaml.Controls.ListViewItem")
        type("Microsoft.UI.Xaml.Controls.MenuFlyout")
        type("Microsoft.UI.Xaml.Controls.MenuFlyoutItem")
        type("Microsoft.UI.Xaml.Controls.MenuFlyoutItemBase")
        type("Microsoft.UI.Xaml.Controls.Orientation")
        type("Microsoft.UI.Xaml.Controls.Page")
        type("Microsoft.UI.Xaml.Controls.ProgressBar")
        type("Microsoft.UI.Xaml.Controls.Primitives.ButtonBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.FlyoutBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.RangeBase")
        type("Microsoft.UI.Xaml.Controls.Primitives.Selector")
        type("Microsoft.UI.Xaml.Controls.Primitives.SelectorItem")
        type("Microsoft.UI.Xaml.Controls.RowDefinition")
        type("Microsoft.UI.Xaml.Controls.Slider")
        type("Microsoft.UI.Xaml.Controls.StackPanel")
        type("Microsoft.UI.Xaml.Controls.Symbol")
        type("Microsoft.UI.Xaml.Controls.SymbolIcon")
        type("Microsoft.UI.Xaml.Controls.TabView")
        type("Microsoft.UI.Xaml.Controls.TabViewItem")
        type("Microsoft.UI.Xaml.Controls.TabViewWidthMode")
        type("Microsoft.UI.Xaml.Controls.TextBox")
        type("Microsoft.UI.Xaml.Controls.TextBlock")
        type("Microsoft.UI.Xaml.Controls.TextWrapping")
        type("Microsoft.UI.Xaml.Controls.ToolTipService")
        type("Microsoft.UI.Xaml.Controls.ToggleSwitch")
        type("Microsoft.UI.Xaml.Controls.Viewbox")
        type("Microsoft.UI.Xaml.Controls.WebView2")
        type("Microsoft.UI.Xaml.Controls.XamlControlsResources")
        type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationCompletedEventArgs")
        type("Microsoft.Web.WebView2.Core.CoreWebView2NavigationStartingEventArgs")
        type("Microsoft.UI.Xaml.Input.KeyEventHandler")
        type("Microsoft.UI.Xaml.Input.KeyRoutedEventArgs")
        type("Microsoft.UI.Xaml.Input.TappedEventHandler")
        type("Microsoft.UI.Xaml.Input.TappedRoutedEventArgs")
        type("Microsoft.UI.Xaml.Markup.XamlReader")
        type("Microsoft.UI.Xaml.Media.MicaBackdrop")
        type("Microsoft.UI.Xaml.Media.SystemBackdrop")
        type("WinUI3Package.SettingsCard")
        type("WinUI3Package.Shimmer")
        type("Windows.System.VirtualKey")
        type("Windows.UI.Xaml.Interop.Type")
        type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
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

val sampleJvmOptionProperties = listOf(
    "kotlin.winrt.samples.runNativeSmoke",
    "kotlin.winrt.samples.runComponentSmoke",
    "kotlin.winrt.samples.runWinUiSmoke",
    "kotlin.winrt.samples.runWebView2Sample",
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

val webView2UserDataRoot = layout.buildDirectory.dir("kotlin-winrt/webview2-user-data")

tasks.named<io.github.composefluent.winrt.gradle.RunWinRTApplicationHostTask>("runWinRTApplicationHostWinuiJvmMain") {
    environmentVariables.put(
        "WEBVIEW2_USER_DATA_FOLDER",
        webView2UserDataRoot.map { it.dir("jvm").asFile.absolutePath },
    )
    jvmArgs.addAll(
        providers.provider {
            sampleJvmOptionProperties.map { name ->
                "-D$name=${providers.systemProperty(name).orElse(standardSampleSmokeDefaults[name] ?: "false").get()}"
            }
        },
    )
}

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    environment(
        "WEBVIEW2_USER_DATA_FOLDER",
        webView2UserDataRoot.get().dir("mingwX64").asFile.absolutePath,
    )
    sampleJvmOptionProperties.forEach { name ->
        providers.systemProperty(name)
            .orElse(standardSampleSmokeDefaults[name] ?: "")
            .orNull
            ?.takeIf { it.isNotEmpty() }
            ?.let { value -> environment(name, value) }
    }
}

// Keep sample smoke and generated-output checks out of the application model.
apply(from = layout.projectDirectory.file("sample-validation.gradle.kts"))
