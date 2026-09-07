plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
    .orElse("2.2.0")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")
val sampleApplicationTarget = providers.gradleProperty("kotlinWinRT.samples.applicationTarget")
    .orElse("winuiJvm")
val sampleNativeBuildType = providers.gradleProperty("kotlinWinRT.samples.nativeBuildType")
    .orElse("release")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            executable()
        }
    }
    sourceSets {
        winuiMain {
            dependencies {
                implementation(project(":winrt-samples:winui-kmp-library"))
            }
        }
    }
}

winRT {
    application {
        mainClass.set("io.github.composefluent.winrt.samples.kmp.app.MainKt")
        targetName.set(sampleApplicationTarget)
        nativeBuildType.set(sampleNativeBuildType)
    }
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
        type("Windows.Foundation.Uri")
        type("Windows.System.Launcher")
        type("Microsoft.UI.Xaml.Automation.AutomationProperties")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.TextBox")
    }
}

val auditGeneratedWinuiKmpProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask::class,
) {
    group = "verification"
    description = "Fails if generated KMP WinUI projection source leaks fallback invocation or JVM-only reflection paths."
    dependsOn("generateWinRTProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
    generatedSourcesDirectory.set(generatedSources)
}

private val winuiKmpOptionProperties = listOf(
    "kotlin.winrt.samples.autoExitWinUi",
    "kotlin.winrt.samples.timerSmoke",
    "kotlin.winrt.samples.skipWindowContent",
    "kotlin.winrt.samples.skipCallbackSmoke",
    "kotlin.winrt.samples.skipLayoutUpdated",
    "KOTLIN_WINRT_TRACE_CCW",
)

tasks.named<io.github.composefluent.winrt.gradle.RunWinRTApplicationHostTask>("runWinRTApplicationHost") {
    jvmArgs.addAll(
        providers.provider {
            winuiKmpOptionProperties.map { name ->
                val defaultValue = if (name == "kotlin.winrt.samples.autoExitWinUi") "true" else ""
                "-D$name=${providers.systemProperty(name).orElse(defaultValue).get()}"
            }
        },
    )
}

// Keep sample smoke and generated-output checks out of the application model.
apply(from = layout.projectDirectory.file("sample-validation.gradle.kts"))
