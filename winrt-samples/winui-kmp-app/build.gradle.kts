plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.windows-toolkit")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkVersion")
    .orElse("2.5.1")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            executable {
                // Missing KLIB symbols must fail linking rather than become runtime traps.
                freeCompilerArgs += "-Xpartial-linkage-loglevel=error"
            }
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

windows {
    application {
        mainClass = "io.github.composefluent.winrt.samples.kmp.app.MainKt"
    }
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        packageReferences {
            windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false)
            nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
                generateProjection = false
            }        }
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
    "kotlin.winrt.samples.autoExitWinUI",
    "kotlin.winrt.samples.timerSmoke",
    "kotlin.winrt.samples.skipWindowContent",
    "kotlin.winrt.samples.skipCallbackSmoke",
    "kotlin.winrt.samples.skipLayoutUpdated",
    "KOTLIN_WINRT_TRACE_CCW",
)

tasks.named<io.github.composefluent.windows.toolkit.gradle.RunWinAppHostTask>("runWinAppHostWinuiJvmMain") {
    jvmArgs.addAll(
        providers.provider {
            winuiKmpOptionProperties.map { name ->
                val defaultValue = if (name == "kotlin.winrt.samples.autoExitWinUI") "true" else ""
                "-D$name=${providers.systemProperty(name).orElse(defaultValue).get()}"
            }
        },
    )
}

// Keep sample smoke and generated-output checks out of the application model.
apply(from = layout.projectDirectory.file("sample-validation.gradle.kts"))
