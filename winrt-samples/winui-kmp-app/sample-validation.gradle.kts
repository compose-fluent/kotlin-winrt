import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec

val verifyWinuiKmpTransitiveProjectionSuppression by tasks.registering {
    group = "verification"
    description = "Verifies the KMP WinUI application suppresses projection types owned by transitive WinRT libraries."
    dependsOn("generateWinRTProjections")
    val generatedSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
    inputs.files(generatedSources)

    doLast {
        val generatedRoot = generatedSources.get().asFile
        val duplicateSources = generatedRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .map { it.relativeTo(generatedRoot).invariantSeparatorsPath }
            .filter { it.startsWith("windows/") || it.startsWith("microsoft/") }.toList()
        check(duplicateSources.isEmpty()) {
            "Application regenerated SDK types owned by the shared prebuilt projections: $duplicateSources"
        }
    }
}

val runWinuiKmpSample by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs the KMP WinRT library consumed by a KMP WinApp sample."
    dependsOn("compileKotlinWinuiJvm")
    dependsOn("stageWindowsPackageRuntimeAssetsWinuiJvmMain")
    mainClass.set("io.github.composefluent.winrt.samples.kmp.app.MainKt")
    classpath(
        layout.buildDirectory.dir("classes/kotlin/winuiJvm/main"),
        configurations.named("winuiJvmRuntimeClasspath"),
    )
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    systemProperty(
        "kotlin.winrt.runtimeAssetsRoot",
        layout.buildDirectory.dir("kotlin-winrt/application-layout/winuiJvm_main/runtime-assets").get().asFile.absolutePath,
    )
    systemProperty(
        "kotlin.winrt.samples.autoExitWinUI",
        providers.systemProperty("kotlin.winrt.samples.autoExitWinUI").orElse("true").get(),
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

private val winuiKmpOptionProperties = listOf(
    "kotlin.winrt.samples.autoExitWinUI",
    "kotlin.winrt.samples.timerSmoke",
    "kotlin.winrt.samples.skipWindowContent",
    "kotlin.winrt.samples.skipCallbackSmoke",
    "kotlin.winrt.samples.skipLayoutUpdated",
    "KOTLIN_WINRT_TRACE_CCW",
)

tasks.named<Exec>("runReleaseExecutableMingwX64") {
    environment(
        "kotlin.winrt.samples.autoExitWinUI",
        providers.systemProperty("kotlin.winrt.samples.autoExitWinUI").orElse("true").get(),
    )
    winuiKmpOptionProperties.filterNot { it == "kotlin.winrt.samples.autoExitWinUI" }.forEach { name ->
        providers.systemProperty(name).orNull?.let { value ->
            environment(name, value)
        }
    }
}

val verifyWinuiKmpJvmRun by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI sample through the generated JVM application host path."
    dependsOn(tasks.named("runWinAppHostWinuiJvmMain"))
}

val verifyWinuiKmpMingwRun by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI sample through the mingwX64 executable path."
    dependsOn(tasks.named("runReleaseExecutableMingwX64"))
}

val verifyWinuiKmpNativeProjectionReachability by tasks.registering {
    group = "verification"
    description = "Checks that the Native WinUI executable does not retain unrelated full-SDK registration roots."
    dependsOn("linkReleaseExecutableMingwX64")
    val executable = layout.buildDirectory.file("bin/mingwX64/releaseExecutable/winui-kmp-app.exe")
    inputs.file(executable)
    doLast {
        // CsWinRT keeps a canonical SDK implementation; the Native adaptation must allow
        // unused registrations to disappear with unused code. These names were all present
        // in the former full-owner initializer's executable, including this UI-only sample.
        val bytes = executable.get().asFile.readBytes().toString(Charsets.ISO_8859_1)
        fun containsRuntimeName(name: String): Boolean = bytes.contains(
            name.toByteArray(Charsets.UTF_16LE).toString(Charsets.ISO_8859_1),
        )
        check(containsRuntimeName("Microsoft.UI.Xaml.Controls.Button")) {
            "Expected the sample's used Button runtime type to remain in the Native executable."
        }
        listOf("Windows.Devices.Bluetooth.BluetoothLEDevice", "Windows.Media.Capture.MediaCapture").forEach { name ->
            check(!containsRuntimeName(name)) { "Unrelated SDK registration is still rooted in the Native executable: $name" }
        }
    }
}

verifyWinuiKmpMingwRun { dependsOn(verifyWinuiKmpNativeProjectionReachability) }

val verifyWinuiKmpNativeComposableAuthoringHost by tasks.registering {
    group = "verification"
    description = "Verifies the KMP WinUI dependency is a native authored DLL with composable/overridable support."
    val library = project(":winrt-samples:winui-kmp-library")
    dependsOn(library.tasks.named("validateCompileKotlinMingwX64WinRTNativeAuthoringExports"))
    dependsOn(library.tasks.named("validateCompileKotlinMingwX64WinRTAuthoredCandidates"))
    dependsOn("${library.path}:releaseSharedWinRTDemandImplementation")
    dependsOn("stageWindowsPackageRuntimeAssetsMingwX64MainReleaseExecutable")

    val nativeAuthoringRoot = library.layout.buildDirectory.dir("kotlin-winrt/native-authoring/compileKotlinMingwX64")
    val generatedTypeDetailsRoot =
        library.layout.buildDirectory.dir("generated/kotlin-winrt-compiler-authoring/compileKotlinMingwX64/src/commonMain/kotlin/sourceSets/winuiMain")
    val sdkGeneratedRoot = library.layout.buildDirectory.dir("kotlin-winrt/native-demand/releaseShared/sources")
    val libraryDll = library.layout.buildDirectory.file("bin/mingwX64/releaseShared/winui_kmp_library.dll")
    val stagedRuntimeAssets =
        layout.buildDirectory.dir("kotlin-winrt/application-layout/mingwX64_main_releaseExecutable/runtime-assets")

    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt/authored-candidates.tsv") })
    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt-authoring/authored-metadata.tsv") })
    inputs.file(nativeAuthoringRoot.map { it.file("kotlin-winrt-authoring/winui-kmp-library.host.json") })
    inputs.file(libraryDll)
    inputs.dir(generatedTypeDetailsRoot)
    inputs.dir(sdkGeneratedRoot)
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
            "WinUIKmpLibraryApp\tio.github.composefluent.winrt.samples.kmp.library.WinUIKmpLibraryApp\tMicrosoft.UI.Xaml.Application\tMicrosoft.UI.Xaml.IApplicationOverrides",
            "WinUIKmpLocalContentControl\tio.github.composefluent.winrt.samples.kmp.library.WinUIKmpLocalContentControl\tMicrosoft.UI.Xaml.Controls.ContentControl",
            "WinUIKmpLocalPanel\tio.github.composefluent.winrt.samples.kmp.library.WinUIKmpLocalPanel\tMicrosoft.UI.Xaml.Controls.Panel",
            "WinUIKmpLocalAutomationPeer\tio.github.composefluent.winrt.samples.kmp.library.WinUIKmpLocalAutomationPeer\tMicrosoft.UI.Xaml.Automation.Peers.AutomationPeer",
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
        check(metadata.contains("WinUIKmpLibraryApp\tMicrosoft.UI.Xaml.Application")) {
            "Expected public WinUI Application authored metadata row: $metadata"
        }
        check(!metadata.contains("WinUIKmpLocalPanel\t")) {
            "Internal composable helper types must stay TypeDetails-only and not become exported activatable metadata: $metadata"
        }

        val hostManifest = requireFile(
            nativeAuthoringRoot.get().file("kotlin-winrt-authoring/winui-kmp-library.host.json").asFile,
            "native authored host manifest",
        ).readText()
        check(hostManifest.contains("\"targetArtifact\": \"winui_kmp_library.dll\"")) {
            "Expected native host manifest to target the mingw shared library: $hostManifest"
        }
        check(hostManifest.contains("WinUIKmpLibraryApp")) {
            "Expected native host manifest to expose the public WinUI application runtime class: $hostManifest"
        }
        check(!hostManifest.contains("WinUIKmpLocalPanel")) {
            "Internal composable helper types must not be exported as activatable classes: $hostManifest"
        }

        val dllBytes = requireFile(libraryDll.get().asFile, "linked mingw authored shared library")
            .readBytes().toString(Charsets.ISO_8859_1)
        fun containsRuntimeName(name: String): Boolean = dllBytes.contains(
            name.toByteArray(Charsets.UTF_16LE).toString(Charsets.ISO_8859_1),
        )
        check(containsRuntimeName("Microsoft.UI.Xaml.Controls.Button")) {
            "Expected the used Button runtime type in the authored DLL."
        }
        listOf("Windows.Devices.Bluetooth.BluetoothLEDevice", "Windows.Media.Capture.MediaCapture").forEach { name ->
            check(!containsRuntimeName(name)) { "Unrelated SDK type retained by the authored DLL: $name" }
        }
        val typeDetailsRoot = generatedTypeDetailsRoot.get().asFile
        val panelTypeDetails = requireFile(
            typeDetailsRoot.resolve(
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUIKmpLocalPanel_TypeDetails.kt",
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
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUIKmpLocalGridHostPanel_TypeDetails.kt",
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
                "io/github/composefluent/winrt/samples/kmp/library/WinRT_WinUIKmpLocalAutomationPeer_TypeDetails.kt",
            ),
            "generated local AutomationPeer TypeDetails",
        ).readText()
        check(automationPeerTypeDetails.contains("__winrtAuthoringInvokeGetPeerFromPointCore")) {
            "Expected local AutomationPeer TypeDetails to dispatch GetPeerFromPointCore through generated bridge."
        }

        val projectedControls = sdkGeneratedRoot.get().asFile.walkTopDown()
            .filter { it.isFile && it.extension == "kt" &&
                it.parentFile.invariantSeparatorsPath.endsWith("/microsoft/ui/xaml/controls") }
            .joinToString("\n") { it.readText() }
        val buttonProjection = sdkGeneratedRoot.get().asFile.walkTopDown().single {
            it.isFile && it.invariantSeparatorsPath.endsWith("/microsoft/ui/xaml/controls/Button.kt")
        }.readText()
        check(!buttonProjection.contains("public val flyoutProperty:")) {
            "Unused Button static member must not be generated in the demand implementation."
        }
        val buttonStatics = sdkGeneratedRoot.get().asFile.walkTopDown().single {
            it.isFile && it.invariantSeparatorsPath.endsWith("/microsoft/ui/xaml/controls/IButtonStatics.kt")
        }.readText()
        check(buttonStatics.contains("FLYOUTPROPERTY_GETTER_SLOT: Int = 6") &&
            !buttonStatics.contains("class NativeProjection")) {
            "A static-only ABI interface must retain its original slots without an unused object proxy."
        }
        val automationProperties = sdkGeneratedRoot.get().asFile.walkTopDown().single {
            it.isFile && it.invariantSeparatorsPath.endsWith("/microsoft/ui/xaml/automation/AutomationProperties.kt")
        }.readText()
        check(automationProperties.contains("public fun setAccessibilityView(")) {
            "A static member used by a transitive business library must be generated."
        }
        check(projectedControls.contains("ComposableFactory.createInstanceForSubclass(this,")) {
            "Expected generated WinUI projection to use composable factory subclass creation."
        }
        check(!projectedControls.contains("ComVtableInvoker.invokeGenericArgs")) {
            "Generated WinUI composable projection must not use generic vtable fallback."
        }

        val stagedRoot = stagedRuntimeAssets.get().asFile
        requireFile(stagedRoot.resolve(libraryDll.get().asFile.name), "staged native authored dependency DLL")
        requireFile(stagedRoot.resolve("winui-kmp-library.host.json"), "staged native authored dependency host manifest")
        requireFile(stagedRoot.resolve("winui-kmp-library.winmd"), "staged native authored dependency WinMD")
    }
}

val validateWinuiKmpIntegration by tasks.registering {
    group = "verification"
    description = "Runs the KMP WinUI projection, authoring, and JVM/native smoke checks."
    dependsOn("auditGeneratedWinuiKmpProjectionOutput")
    dependsOn(verifyWinuiKmpTransitiveProjectionSuppression)
    dependsOn(verifyWinuiKmpNativeComposableAuthoringHost)
    dependsOn(verifyWinuiKmpJvmRun)
    dependsOn(verifyWinuiKmpMingwRun)
}
