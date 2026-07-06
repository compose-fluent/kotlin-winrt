import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
    .orElse("2.1.3")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsSdkVersion")
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
    }
}

winRT {
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
    dependsOn("generateWinRTProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
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
    io.github.composefluent.winrt.gradle.ValidateGeneratedWinRTProjectionOutputTask::class,
) {
    group = "verification"
    description = "Fails if generated KMP WinUI projection source leaks fallback invocation or JVM-only reflection paths."
    dependsOn("generateWinRTProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
    generatedSourcesDirectory.set(generatedSources)
}

val runWinuiKmpSample by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the KMP WinRT library consumed by a KMP WinRT application sample."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn("stageWinRTRuntimeAssets")
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

tasks.named<io.github.composefluent.winrt.gradle.BuildWinRTApplicationHostTask>("buildWinRTApplicationHost") {
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

tasks.named<Exec>("runWinRTApplicationHost") {
    val hostJvmOptions = winuiKmpOptionProperties.joinToString(";") { name ->
        val defaultValue = if (name == "kotlin.winrt.samples.autoExitWinUi") "true" else ""
        "-D$name=${providers.systemProperty(name).orElse(defaultValue).get()}"
    }
    environment("KOTLIN_WINRT_JVM_OPTIONS", hostJvmOptions)
}

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    dependsOn("stageWinRTApplicationPackage")
    workingDir(layout.buildDirectory.dir("kotlin-winrt/application-layout/mingwX64/release"))
    executable(layout.buildDirectory.file("kotlin-winrt/application-layout/mingwX64/release/${project.name}.exe").get().asFile.absolutePath)
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
    dependsOn(tasks.named("runWinRTApplicationHost"))
}

val verifyWinuiKmpMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI sample through the mingwX64 executable path."
    dependsOn(tasks.named("runReleaseExecutableMingwX64"))
}

val verifyWinuiKmpNativeComposableAuthoringHost by tasks.registering {
    group = "verification"
    description = "Verifies the KMP WinUI dependency is a native authored DLL with composable/overridable support."
    val library = project(":winrt-samples:winui-kmp-library")
    dependsOn(library.tasks.named("validateCompileKotlinMingwX64WinRTNativeAuthoringExports"))
    dependsOn("stageWinRTRuntimeAssets")

    val nativeAuthoringRoot = library.layout.buildDirectory.dir("kotlin-winrt/native-authoring/compileKotlinMingwX64")
    val generatedTypeDetailsRoot =
        library.layout.buildDirectory.dir("generated/kotlin-winrt-compiler-authoring/compileKotlinMingwX64/src/commonMain/kotlin")
    val libraryGeneratedRoot = library.layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
    val libraryDll = library.layout.buildDirectory.file("bin/mingwX64/releaseShared/winui_kmp_library.dll")
    val stagedRuntimeAssets = layout.buildDirectory.dir("kotlin-winrt/runtime-assets")

    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt/authored-candidates.tsv") })
    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt-authoring/authored-metadata.tsv") })
    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt-authoring/winui-kmp-library.host.json") })
    inputs.file(libraryDll)
    inputs.dir(generatedTypeDetailsRoot)
    inputs.dir(libraryGeneratedRoot)
    inputs.dir(stagedRuntimeAssets)

    doLast {
        fun requireFile(file: java.io.File, description: String): java.io.File {
            check(file.isFile) { "Expected $description: $file" }
            return file
        }

        val candidates = requireFile(
            nativeAuthoringRoot.get().file("kotlin-winrt/authored-candidates.tsv").asFile,
            "compiler-authored candidate table",
        ).readText()
        listOf(
            "WinUiKmpLibraryApp\tio.github.composefluent.winrt.samples.kmp.library.WinUiKmpLibraryApp\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides",
            "WinUiKmpLocalContentControl\tio.github.composefluent.winrt.samples.kmp.library.WinUiKmpLocalContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl",
            "WinUiKmpLocalPanel\tio.github.composefluent.winrt.samples.kmp.library.WinUiKmpLocalPanel\tMicrosoft.UI.Xaml.Controls.Panel",
            "WinUiKmpLocalAutomationPeer\tio.github.composefluent.winrt.samples.kmp.library.WinUiKmpLocalAutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.AutomationPeer",
        ).forEach { expected ->
            check(candidates.contains(expected)) {
                "Expected native authored candidate row containing '$expected':\n$candidates"
            }
        }
        listOf(
            "Microsoft.UI.Xaml.IFrameworkElementOverrides",
            "Microsoft.UI.Xaml.IUIElementOverrides",
            "Microsoft.UI.Xaml.Automation.Peers.IAutomationPeerOverrides",
        ).forEach { expectedInterface ->
            check(candidates.contains(expectedInterface)) {
                "Expected native authored candidates to carry overridable interface '$expectedInterface':\n$candidates"
            }
        }

        val metadata = requireFile(
            nativeAuthoringRoot.get().file("kotlin-winrt-authoring/authored-metadata.tsv").asFile,
            "compiler-authored metadata descriptor",
        ).readText()
        check(metadata.contains("WinUiKmpLibraryApp\tMicrosoft.UI.Xaml.Application")) {
            "Expected public WinUI Application authored metadata row: $metadata"
        }
        check(!metadata.contains("WinUiKmpLocalPanel\t")) {
            "Internal composable helper types must stay TypeDetails-only and not become exported activatable metadata: $metadata"
        }

        val hostManifest = requireFile(
            nativeAuthoringRoot.get().file("kotlin-winrt-authoring/winui-kmp-library.host.json").asFile,
            "native authored host manifest",
        ).readText()
        check(hostManifest.contains("\"targetArtifact\": \"winui_kmp_library.dll\"")) {
            "Expected native host manifest to target the mingw shared library: $hostManifest"
        }
        check(hostManifest.contains("WinUiKmpLibraryApp")) {
            "Expected native host manifest to expose the public WinUI application runtime class: $hostManifest"
        }
        check(!hostManifest.contains("WinUiKmpLocalPanel")) {
            "Internal composable helper types must not be exported as activatable classes: $hostManifest"
        }

        requireFile(libraryDll.get().asFile, "linked mingw authored shared library")
        val typeDetailsRoot = generatedTypeDetailsRoot.get().asFile
        val panelTypeDetails = requireFile(
            typeDetailsRoot.resolve(
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUiKmpLocalPanel_TypeDetails.kt",
            ),
            "generated local Panel TypeDetails",
        ).readText()
        check(panelTypeDetails.contains("__winrtAuthoringInvokeMeasureOverride")) {
            "Expected local Panel TypeDetails to dispatch FrameworkElement override bridges: $panelTypeDetails"
        }
        check(panelTypeDetails.contains("__winrtAuthoringInvokeOnCreateAutomationPeer")) {
            "Expected local Panel TypeDetails to dispatch UIElement automation peer override bridge: $panelTypeDetails"
        }
        val gridHostTypeDetails = requireFile(
            typeDetailsRoot.resolve(
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUiKmpLocalGridHostPanel_TypeDetails.kt",
            ),
            "generated local Grid host TypeDetails",
        ).readText()
        check(gridHostTypeDetails.contains("__winrtAuthoringInvokeMeasureOverride")) {
            "Expected local Grid host TypeDetails to dispatch FrameworkElement measure override bridge: $gridHostTypeDetails"
        }
        check(gridHostTypeDetails.contains("__winrtAuthoringInvokeArrangeOverride")) {
            "Expected local Grid host TypeDetails to dispatch FrameworkElement arrange override bridge: $gridHostTypeDetails"
        }
        check(gridHostTypeDetails.contains("__winrtAuthoringInvokeOnCreateAutomationPeer")) {
            "Expected local Grid host TypeDetails to dispatch UIElement automation peer override bridge: $gridHostTypeDetails"
        }
        val automationPeerTypeDetails = requireFile(
            typeDetailsRoot.resolve(
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUiKmpLocalAutomationPeer_TypeDetails.kt",
            ),
            "generated local AutomationPeer TypeDetails",
        ).readText()
        check(automationPeerTypeDetails.contains("__winrtAuthoringInvokeGetPeerFromPointCore")) {
            "Expected local AutomationPeer TypeDetails to dispatch GetPeerFromPointCore through generated bridge."
        }

        val projectedControls = libraryGeneratedRoot.get().asFile
            .resolve("microsoft/ui/xaml/controls/microsoft_ui_xaml_controls_1.kt")
            .readText()
        check(projectedControls.contains("ComposableFactory.createInstanceForSubclass(this,")) {
            "Expected generated WinUI projection to use composable factory subclass creation."
        }
        check(!projectedControls.contains("ComVtableInvoker.invokeGenericArgs")) {
            "Generated WinUI composable projection must not use generic vtable fallback."
        }

        val stagedRoot = stagedRuntimeAssets.get().asFile
        requireFile(stagedRoot.resolve("winui-kmp-library.dll"), "staged native authored dependency DLL")
        requireFile(stagedRoot.resolve("winui-kmp-library.host.json"), "staged native authored dependency host manifest")
        requireFile(stagedRoot.resolve("winui-kmp-library.winmd"), "staged native authored dependency WinMD")
    }
}

tasks.named("check") {
    dependsOn(auditGeneratedWinuiKmpProjectionOutput)
    dependsOn(verifyWinuiKmpTransitiveProjectionSuppression)
    dependsOn(verifyWinuiKmpNativeComposableAuthoringHost)
    dependsOn(verifyWinuiKmpJvmRun)
    dependsOn(verifyWinuiKmpMingwRun)
}
