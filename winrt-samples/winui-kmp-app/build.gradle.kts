import org.gradle.api.tasks.JavaExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
    .orElse("2.1.3")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":winrt-samples:winui-kmp-library"))
            }
        }
        val winuiMain by creating {
            dependsOn(commonMain.get())
        }
        named("winuiJvmMain") {
            dependsOn(winuiMain)
        }
    }
}

winRt {
    application {
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

val verifyWinuiKmpTransitiveProjectionSuppression by tasks.registering {
    group = "verification"
    description = "Verifies the KMP WinUI application suppresses projection types owned by transitive WinRT libraries."
    dependsOn("generateWinRtProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
    inputs.files(generatedSources)

    doLast {
        val generatedRoot = generatedSources.get().asFile
        check(!generatedRoot.resolve("windows/system/Launcher.kt").exists()) {
            "Application regenerated Windows.System.Launcher owned by the transitive KMP base library."
        }
        check(!generatedRoot.resolve("microsoft/ui/xaml/controls/Button.kt").exists()) {
            "Application regenerated Microsoft.UI.Xaml.Controls.Button owned by the direct KMP WinUI library."
        }
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
    providers.systemProperty("kotlin.winrt.samples.timerSmoke").orNull?.let { value ->
        systemProperty("kotlin.winrt.samples.timerSmoke", value)
    }
    listOf(
        "kotlin.winrt.samples.skipWindowContent",
        "kotlin.winrt.samples.skipCallbackSmoke",
        "kotlin.winrt.samples.skipLayoutUpdated",
    ).forEach { propertyName ->
        providers.systemProperty(propertyName).orNull?.let { value ->
            systemProperty(propertyName, value)
        }
    }
    providers.systemProperty("KOTLIN_WINRT_TRACE_CCW").orNull?.let { value ->
        systemProperty("KOTLIN_WINRT_TRACE_CCW", value)
    }
}

tasks.named("check") {
    dependsOn(verifyWinuiKmpTransitiveProjectionSuppression)
}
