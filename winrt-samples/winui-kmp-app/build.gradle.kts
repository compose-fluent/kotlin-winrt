import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")

kotlin {
    jvmToolchain(22)
    jvm("winuiJvm")
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":winrt-runtime"))
                implementation(project(":winrt-samples:winui-kmp-library"))
            }
        }
    }
}

winRt {
    application {
    }
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
    }
}

val runWinuiKmpSample by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the KMP WinRT library consumed by a KMP WinRT application sample."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn("stageWinRtRuntimeAssets")
    mainClass.set("io.github.composefluent.winrt.samples.kmp.app.MainKt")
    classpath(
        layout.buildDirectory.dir("classes/kotlin/winuiJvm/main"),
        configurations.named("winuiJvmRuntimeClasspath"),
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kotlin.winrt.runtimeAssetsRoot",
        layout.buildDirectory.dir("kotlin-winrt/runtime-assets").get().asFile.absolutePath,
    )
    systemProperty(
        "kotlin.winrt.samples.autoExitWinUi",
        providers.systemProperty("kotlin.winrt.samples.autoExitWinUi").orElse("true").get(),
    )
}
