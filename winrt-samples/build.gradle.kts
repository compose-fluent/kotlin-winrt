plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt")
    application
}

val sampleWindowsAppSdkWinuiVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkWinuiVersion")
val sampleWindowsAppSdkFoundationVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkFoundationVersion")
val sampleWindowsAppSdkInteractiveExperiencesVersion =
    providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion")

kotlin {
    jvmToolchain(22)
    if (sampleWindowsAppSdkWinuiVersion.orNull != null) {
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
    implementation(projects.winrtProjections)
    testImplementation(libs.junit)
}

winRt {
    type("Windows.Foundation.IStringable")
    namespace("Windows.Data.Json")
    application {
    }
    sampleWindowsAppSdkWinuiVersion.orNull?.let { winuiVersion ->
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK.Foundation", sampleWindowsAppSdkFoundationVersion.orNull ?: winuiVersion)
        nugetPackage(
            "Microsoft.WindowsAppSDK.InteractiveExperiences",
            sampleWindowsAppSdkInteractiveExperiencesVersion.orNull ?: winuiVersion,
        )
        nugetPackage("Microsoft.WindowsAppSDK.WinUI", winuiVersion)
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
        type("Windows.UI.Xaml.Markup.ContentPropertyAttribute")
        type("Windows.UI.Xaml.StyleTypedPropertyAttribute")
        type("Windows.UI.Xaml.TemplatePartAttribute")
        type("Windows.UI.Xaml.TemplateVisualStateAttribute")
        type("Windows.UI.Xaml.Data.BindableAttribute")
        type("Windows.UI.Xaml.Markup.FullXamlMetadataProviderAttribute")
        type("Windows.UI.Xaml.Markup.MarkupExtensionReturnTypeAttribute")
        type("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
        type("Windows.UI.Xaml.Media.Animation.IndependentlyAnimatableAttribute")
    }
}

application {
    mainClass = "io.github.kitectlab.winrt.samples.MainKt"
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
        val expectedWinuiVersion = sampleWindowsAppSdkWinuiVersion.orNull
        if (expectedWinuiVersion == null) {
            check("Microsoft.WindowsAppSDK" !in identityJson) {
                "WindowsAppSDK should only be declared when kotlinWinRt.samples.windowsAppSdkWinuiVersion is set."
            }
        } else {
            val expectedFoundationVersion = sampleWindowsAppSdkFoundationVersion.orNull ?: expectedWinuiVersion
            val expectedInteractiveExperiencesVersion = sampleWindowsAppSdkInteractiveExperiencesVersion.orNull ?: expectedWinuiVersion
            val expectedPackages = listOf(
                "Microsoft.WindowsAppSDK.Foundation@$expectedFoundationVersion",
                "Microsoft.WindowsAppSDK.InteractiveExperiences@$expectedInteractiveExperiencesVersion",
                "Microsoft.WindowsAppSDK.WinUI@$expectedWinuiVersion",
            )
            expectedPackages.forEach { expectedPackage ->
                check(expectedPackage in identityJson) {
                    "Expected sample application identity JSON to include $expectedPackage."
                }
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
