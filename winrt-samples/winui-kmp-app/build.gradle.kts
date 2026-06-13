import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar

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
    mingwX64 {
        binaries {
            executable()
        }
    }
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
        named("mingwX64Main") {
            dependsOn(winuiMain)
        }
    }
}

winRt {
    application {
        mainClass.set("io.github.composefluent.winrt.samples.kmp.app.MainKt")
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

val auditGeneratedWinuiKmpProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.gradle.ValidateGeneratedWinRtProjectionOutputTask::class,
) {
    group = "verification"
    description = "Fails if generated KMP WinUI projection source leaks fallback invocation or JVM-only reflection paths."
    dependsOn("generateWinRtProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/main/kotlin")
    generatedSourcesDirectory.set(generatedSources)
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

tasks.named<io.github.composefluent.winrt.gradle.BuildWinRtApplicationHostTask>("buildWinRtApplicationHost") {
    val winuiJvmJar = tasks.named<Jar>("winuiJvmJar")
    val defaultJarName = providers.provider { "${project.name}-${project.version}.jar" }
    dependsOn(winuiJvmJar)
    runtimeClasspath.from(winuiJvmJar.flatMap { it.archiveFile })
    runtimeClasspath.from(
        providers.provider {
            configurations.named("winuiJvmRuntimeClasspath").get().filter { file ->
                file.name != defaultJarName.get()
            }
        },
    )
}

private val winuiKmpOptionProperties = listOf(
    "kotlin.winrt.samples.autoExitWinUi",
    "kotlin.winrt.samples.timerSmoke",
    "kotlin.winrt.samples.skipWindowContent",
    "kotlin.winrt.samples.skipCallbackSmoke",
    "kotlin.winrt.samples.skipLayoutUpdated",
    "KOTLIN_WINRT_TRACE_CCW",
)

tasks.named<Exec>("runWinRtApplicationHost") {
    val hostJvmOptions = winuiKmpOptionProperties.joinToString(";") { name ->
        val defaultValue = if (name == "kotlin.winrt.samples.autoExitWinUi") "true" else ""
        "-D$name=${providers.systemProperty(name).orElse(defaultValue).get()}"
    }
    environment("KOTLIN_WINRT_JVM_OPTIONS", hostJvmOptions)
}

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    dependsOn("stageWinRtRuntimeAssets")
    workingDir(projectDir)
    environment(
        "KOTLIN_WINRT_RUNTIME_ASSETS_ROOT",
        layout.buildDirectory.dir("kotlin-winrt/runtime-assets").get().asFile.absolutePath,
    )
    environment(
        "kotlin.winrt.samples.autoExitWinUi",
        providers.systemProperty("kotlin.winrt.samples.autoExitWinUi").orElse("true").get(),
    )
    winuiKmpOptionProperties.filterNot { it == "kotlin.winrt.samples.autoExitWinUi" }.forEach { name ->
        providers.systemProperty(name).orNull?.let { value ->
            environment(name, value)
        }
    }
}

val verifyWinuiKmpJvmRun by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI sample through the generated JVM application host path."
    dependsOn(tasks.named("runWinRtApplicationHost"))
}

val verifyWinuiKmpMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI sample through the mingwX64 executable path."
    dependsOn(tasks.named("runReleaseExecutableMingwX64"))
}

tasks.named("check") {
    dependsOn(auditGeneratedWinuiKmpProjectionOutput)
    dependsOn(verifyWinuiKmpTransitiveProjectionSuppression)
    dependsOn(verifyWinuiKmpJvmRun)
    dependsOn(verifyWinuiKmpMingwRun)
}
