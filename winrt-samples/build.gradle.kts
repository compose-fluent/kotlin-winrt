plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt")
    application
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtProjections)
    testImplementation(libs.junit)
}

val sampleWindowsAppSdkWinuiVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkWinuiVersion")
val sampleWindowsAppSdkFoundationVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkFoundationVersion")
val sampleWindowsAppSdkInteractiveExperiencesVersion =
    providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkInteractiveExperiencesVersion")
winRt {
    type("Windows.Foundation.IStringable")
    application {
    }
    sampleWindowsAppSdkWinuiVersion.orNull?.let { winuiVersion ->
        windowsSdk(includeExtensions = true)
        namespace("Windows.ApplicationModel.Activation")
        namespace("Windows.ApplicationModel.DataTransfer")
        namespace("Windows.Data.Json")
        namespace("Windows.Foundation")
        namespace("Windows.Foundation.Collections")
        namespace("Windows.Graphics")
        namespace("Windows.Storage.Streams")
        namespace("Windows.System")
        namespace("Windows.UI")
        namespace("Windows.Web.Http")
        namespace("Windows.Web.Http.Headers")
        windowsAppSdk(
            winuiVersion = winuiVersion,
            foundationVersion = sampleWindowsAppSdkFoundationVersion.orNull ?: winuiVersion,
            interactiveExperiencesVersion = sampleWindowsAppSdkInteractiveExperiencesVersion.orNull ?: winuiVersion,
        )
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

tasks.named("check") {
    dependsOn(verifyWinRtSampleIdentity)
    dependsOn(verifyWinRtSampleRuntimeAssets)
}
