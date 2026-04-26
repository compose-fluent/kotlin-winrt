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

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")

winRt {
    type("Windows.Foundation.IStringable")
    application {
    }
    sampleWindowsAppSdkVersion.orNull?.let { version ->
        nugetPackage("Microsoft.WindowsAppSDK", version)
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
        val expectedWindowsAppSdkVersion = sampleWindowsAppSdkVersion.orNull
        if (expectedWindowsAppSdkVersion == null) {
            check("Microsoft.WindowsAppSDK" !in identityJson) {
                "WindowsAppSDK should only be declared when kotlinWinRt.samples.windowsAppSdkVersion is set."
            }
        } else {
            check("\"nugetPackages\": [\"Microsoft.WindowsAppSDK@$expectedWindowsAppSdkVersion\"]" in identityJson) {
                "Expected sample application identity JSON to include Microsoft.WindowsAppSDK@$expectedWindowsAppSdkVersion."
            }
        }
    }
}

tasks.named("check") {
    dependsOn(verifyWinRtSampleIdentity)
}
