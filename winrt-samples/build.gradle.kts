plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("io.github.compose-fluent.winrt")
    application
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
val sampleWinUIEssentialVersion = providers.gradleProperty("kotlinWinRt.samples.winUIEssentialVersion")
    .orElse("1.6.7")
val sampleNuGetGlobalPackagesRoot = providers.gradleProperty("kotlinWinRt.samples.nugetGlobalPackagesRoot")

kotlin {
    if (sampleWindowsAppSdkVersion.orNull != null) {
        sourceSets.named("main") {
            kotlin.srcDir("src/winuiSample/kotlin")
        }
        sourceSets.named("test") {
            kotlin.srcDir("src/winuiSampleTest/kotlin")
        }
    } else {
        sourceSets.named("main") {
            kotlin.srcDir("src/noWinuiSample/kotlin")
        }
    }
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtAuthoring)
    implementation(projects.winrtProjections)
    testImplementation(libs.junit)
}

winRt {
    type("Windows.Foundation.IStringable")
    namespace("Windows.Data.Json")
    application {
    }
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        sampleNuGetGlobalPackagesRoot.orNull?.let { globalPackagesRoot ->
            nugetGlobalPackagesRoots.add(globalPackagesRoot)
            useNuGetCliGlobalPackages.set(false)
            restoreNuGetPackages.set(false)
        }
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
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
        type("Microsoft.UI.Xaml.Controls.Orientation")
        type("Microsoft.UI.Xaml.Controls.Page")
        type("Microsoft.UI.Xaml.Controls.Primitives.ButtonBase")
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

application {
    mainClass = "io.github.composefluent.winrt.samples.MainKt"
}

tasks.named<JavaExec>("run") {
    dependsOn(tasks.named("buildWinRtAuthoringHost"))
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kotlin.winrt.samples.runNativeSmoke",
        providers.systemProperty("kotlin.winrt.samples.runNativeSmoke").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.runComponentSmoke",
        providers.systemProperty("kotlin.winrt.samples.runComponentSmoke").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.runWinUiSmoke",
        providers.systemProperty("kotlin.winrt.samples.runWinUiSmoke").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.autoNavigateWinUi",
        providers.systemProperty("kotlin.winrt.samples.autoNavigateWinUi").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.autoExitWinUi",
        providers.systemProperty("kotlin.winrt.samples.autoExitWinUi").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.minimalWinUiSurface",
        providers.systemProperty("kotlin.winrt.samples.minimalWinUiSurface").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipObjectContent",
        providers.systemProperty("kotlin.winrt.samples.skipObjectContent").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipSettingsCard",
        providers.systemProperty("kotlin.winrt.samples.skipSettingsCard").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipShimmer",
        providers.systemProperty("kotlin.winrt.samples.skipShimmer").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.enableShimmerLoading",
        providers.systemProperty("kotlin.winrt.samples.enableShimmerLoading").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipShimmerSizing",
        providers.systemProperty("kotlin.winrt.samples.skipShimmerSizing").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipMica",
        providers.systemProperty("kotlin.winrt.samples.skipMica").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.noWinUiContent",
        providers.systemProperty("kotlin.winrt.samples.noWinUiContent").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipXamlResources",
        providers.systemProperty("kotlin.winrt.samples.skipXamlResources").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipWinUiResourceManager",
        providers.systemProperty("kotlin.winrt.samples.skipWinUiResourceManager").orElse("false").get(),
    )
    systemProperty(
        "kotlin.winrt.samples.skipRuntimeScope",
        providers.systemProperty("kotlin.winrt.samples.skipRuntimeScope").orElse("false").get(),
    )
    systemProperty(
        "KOTLIN_WINRT_TRACE_CCW",
        providers.systemProperty("KOTLIN_WINRT_TRACE_CCW").orElse("false").get(),
    )
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

val verifyWinRtSampleDistribution by tasks.registering {
    group = "verification"
    description = "Verifies the sample application distribution contains staged WinRT runtime assets."
    val distributionRuntimeAssetsDir = layout.buildDirectory.dir("install/${project.name}/kotlin-winrt-runtime-assets")
    dependsOn(tasks.named("installDist"))
    inputs.dir(distributionRuntimeAssetsDir)

    doLast {
        check(distributionRuntimeAssetsDir.get().asFile.resolve("SimpleMathComponent.dll").isFile) {
            "Expected SimpleMathComponent.dll to be included under kotlin-winrt-runtime-assets in the application distribution."
        }
    }
}

val verifyWinRtSampleRun by tasks.registering {
    group = "verification"
    description = "Runs the sample application bootstrap without opt-in native WinRT smoke tests."
    dependsOn(tasks.named("run"))
}

tasks.named("check") {
    dependsOn(verifyWinRtSampleIdentity)
    dependsOn(verifyWinRtSampleRuntimeAssets)
    dependsOn(verifyWinRtSampleDistribution)
    dependsOn(verifyWinRtSampleRun)
}
