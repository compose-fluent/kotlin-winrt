plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt")
    application
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")

kotlin {
    jvmToolchain(22)
    if (sampleWindowsAppSdkVersion.orNull != null) {
        sourceSets.named("main") {
            kotlin.srcDir("src/winuiSample/kotlin")
        }
        sourceSets.named("test") {
            kotlin.srcDir("src/winuiSampleTest/kotlin")
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
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.DependencyProperty")
        type("Microsoft.UI.Xaml.FrameworkElement")
        type("Microsoft.UI.Xaml.HorizontalAlignment")
        type("Microsoft.UI.Xaml.RoutedEventArgs")
        type("Microsoft.UI.Xaml.RoutedEventHandler")
        type("Microsoft.UI.Xaml.UIElement")
        type("Microsoft.UI.Xaml.VerticalAlignment")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.ContentControl")
        type("Microsoft.UI.Xaml.Controls.Page")
        type("Microsoft.UI.Xaml.Controls.Primitives.ButtonBase")
        type("Microsoft.UI.Xaml.Input.TappedEventHandler")
        type("Microsoft.UI.Xaml.Input.TappedRoutedEventArgs")
        type("Windows.UI.Xaml.Interop.Type")
        type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
    }
}

application {
    mainClass = "io.github.kitectlab.winrt.samples.MainKt"
}

tasks.named<JavaExec>("run") {
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
}

val verifyWinRtSampleIdentity by tasks.registering {
    group = "verification"
    description = "Verifies the sample application aggregates Kotlin WinRT identity metadata from projection dependencies."
    val identityFile = layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt-application.json")
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
        val expectedWindowsAppSdkVersion = sampleWindowsAppSdkVersion.orNull
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
