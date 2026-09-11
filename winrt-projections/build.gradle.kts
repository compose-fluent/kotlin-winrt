import io.github.composefluent.winrt.build.VerifyBinaryMarkerAbsentTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.prebuilt-projection") apply false
    id("io.github.compose-fluent.winrt")
}

val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsAppSdkVersion")
    .orElse("2.2.0")
val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionIncludeWinAppSdk = providers.gradleProperty("kotlinWinRT.projections.includeWinAppSdk")
    .map(String::toBooleanStrict)
    .orElse(false)
val projectionIncludeFullWindowsSdk = providers.gradleProperty("kotlinWinRT.projections.includeFullWindowsSdk")
    .map(String::toBooleanStrict)
    .orElse(false)
val fullWindowsSdkProjectionGateRequested = providers.provider {
    val fullWindowsSdkGateTasks = setOf(
        "validateWinRTFullWindowsSdkProjectionGate",
        "validateWinRTMingwParity",
        "validateWinRTProjectionCompile",
        "validateWinRTSampleSmoke",
        "validateWinRTQueue16",
    )
    gradle.startParameter.taskNames.any { taskName ->
        val unqualifiedTaskName = taskName.substringAfterLast(":")
        unqualifiedTaskName in fullWindowsSdkGateTasks
    }
}
val projectionUseFullWindowsSdk = projectionIncludeFullWindowsSdk
    .zip(fullWindowsSdkProjectionGateRequested) { propertyEnabled, gateRequested ->
        propertyEnabled || gateRequested
    }

kotlin {
    jvm {
        compilerOptions {
            // The IR plugin already emits direct fixed-shape ABI bodies. Re-running the legacy
            // JVM peephole optimizer over the full SDK retains millions of ASM analysis nodes.
            freeCompilerArgs.add("-Xno-optimize")
        }
    }
    mingwX64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

val generatedWinRTProjectionSources = layout.buildDirectory.dir("generated/kotlin-winrt/src/winuiMain/kotlin")
val compiledJvmProjectionClasses = layout.buildDirectory.dir(
    "classes/kotlin-winrt/projection/compileKotlinJvm",
)
val compileJvmProjectionTaskName = "compileKotlinWinRTProjectionJvm"

val verifyJvmProjectionCallSiteLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that no generated WinRT call-site placeholder reaches JVM bytecode."
    dependsOn(compileJvmProjectionTaskName)
    binaryArtifacts.from(compiledJvmProjectionClasses)
    markers.set(setOf("Lowered while compiling the generated WinRT module"))
    artifactDescription.set("compiled JVM projection classes")
}

val verifyMingwX64ProjectionCallSiteLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that no generated WinRT call-site placeholder reaches the mingwX64 klib."
    dependsOn("compileKotlinMingwX64")
    binaryArtifacts.from(layout.buildDirectory.dir("classes/kotlin/mingwX64/main/klib"))
    markers.set(setOf("Lowered while compiling the generated WinRT module"))
    artifactDescription.set("compiled mingwX64 projection klib")
}

val verifyMingwX64ProjectionCallSiteDirectLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that Native generated call sites use recipe thunk accessors without the old vtable reads."
    dependsOn("compileKotlinMingwX64")
    binaryArtifacts.from(
        layout.buildDirectory.dir(
            "classes/kotlin/mingwX64/main/klib/winrt-projections/default/linkdata",
        ),
    )
    markers.set(setOf("readPointer", "readPointerAt", "kotlin/TODO"))
    artifactDescription.set("Native projection call-site linkdata")
}

val verifyMingwX64ProjectionThunkAccessors by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that Native generated files contain the recipe thunk accessors used by this projection."
    dependsOn("compileKotlinMingwX64")
    binaryArtifacts.from(
        layout.buildDirectory.file(
            "classes/kotlin/mingwX64/main/klib/winrt-projections/default/ir/debugInfo.knd",
        ),
        layout.buildDirectory.file(
            "classes/kotlin/mingwX64/main/klib/winrt-projections/default/ir/strings.knt",
        ),
    )
    markers.set(emptySet())
    requiredMarkers.set(
        setOf(
            "kotlinWinRTNativeHResultThunk",
            "kotlinWinRTNativePackedScalarResultThunk",
            "kotlinWinRTNativeWideScalarResultThunk",
        ),
    )
    artifactDescription.set("Native projection thunk-accessor metadata")
}

val verifyJvmProjectionCallSiteDirectLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that generated JVM call-site owners contain only direct fixed-shape lowering."
    dependsOn(compileJvmProjectionTaskName)
    binaryArtifacts.from(
        compiledJvmProjectionClasses.map { classesDirectory ->
            classesDirectory.asFileTree.matching {
                include("io/github/composefluent/winrt/projections/support/WinRTModulePlatformAbiCall_*.class")
            }
        },
    )
    markers.set(setOf(
        "confinedScope",
        "allocateBytes",
        "getLayout",
        "WinRTProjectionIntrinsic",
        "kotlin/TODO",
    ))
    methodNamePrefixes.set(setOf("callSite_"))
    artifactDescription.set("compiled JVM module call-site owners")
}

val auditGeneratedWinRTProjectionOutput by tasks.registering(
    io.github.composefluent.winrt.build.ValidatePrebuiltProjectionOutputTask::class,
) {
    group = "verification"
    description = "Fails if generated projection source leaks fallback invocation or JVM-only reflection paths."
    dependsOn("generateWinRTProjections")
    dependsOn(compileJvmProjectionTaskName)
    dependsOn(verifyJvmProjectionCallSiteLowering)
    dependsOn(verifyJvmProjectionCallSiteDirectLowering)
    dependsOn(verifyMingwX64ProjectionCallSiteLowering)
    generatedSourcesDirectory.set(generatedWinRTProjectionSources)
    compiledClassesDirectories.from(compiledJvmProjectionClasses)
    maxTotalClassBytes.set(
        projectionUseFullWindowsSdk.map { useFullWindowsSdk ->
            if (useFullWindowsSdk) 150_000_000L else 75_000_000L
        },
    )
}

tasks.matching { task -> task.name == "jvmJar" }.configureEach {
    dependsOn(verifyJvmProjectionCallSiteLowering)
    dependsOn(verifyJvmProjectionCallSiteDirectLowering)
}

tasks.matching { task -> task.name == "mingwX64MainKlibrary" || task.name == "mingwX64Klib" }.configureEach {
    dependsOn(verifyMingwX64ProjectionCallSiteLowering)
    dependsOn(verifyMingwX64ProjectionCallSiteDirectLowering)
    dependsOn(verifyMingwX64ProjectionThunkAccessors)
}

tasks.named("check") {
    dependsOn(auditGeneratedWinRTProjectionOutput)
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    if (projectionIncludeWinAppSdk.get()) projectionWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
            generateProjection = true
        }
    }

    if (projectionUseFullWindowsSdk.get()) {
        namespace("Windows")
        excludeNamespace("Windows.UI.Xaml")
        excludeNamespace("Windows.ApplicationModel.Store.Preview")
        excludeType("Windows.UI.Colors")
        excludeType("Windows.UI.IColors")
        excludeType("Windows.UI.ColorHelper")
        excludeType("Windows.UI.IColorHelper")
        excludeType("Windows.UI.IColorHelperStatics")
        excludeType("Windows.UI.IColorHelperStatics2")
    } else {
        namespace("Windows.Foundation")
        namespace("Windows.Foundation.Collections")
        namespace("Windows.Data.Json")
        namespace("Windows.System")
        namespace("Windows.ApplicationModel.DataTransfer")
        namespace("Windows.System.Display")
        namespace("Windows.UI.ViewManagement")
        namespace("Windows.UI.Xaml.Interop")
    }
    if (projectionIncludeWinAppSdk.get()) {
        excludeNamespace("Windows.UI.Composition")
        excludeType("Windows.UI.Composition")
        namespace("Microsoft.UI.Dispatching")
        namespace("Microsoft.UI.Windowing")
        namespace("Microsoft.UI.Xaml")
        namespace("Microsoft.UI.Xaml.Automation")
        namespace("Microsoft.UI.Xaml.Automation.Peers")
        namespace("Microsoft.UI.Xaml.Controls")
        namespace("Microsoft.UI.Xaml.Media")
    }
    namespace("SimpleMathComponent")
    winmd(
        providers.gradleProperty("kotlinWinRT.samples.simpleMathWinmd")
            .getOrElse(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.winmd").asFile.absolutePath),
    )
    runtimeAsset(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.dll").asFile.absolutePath)
    type("Windows.Foundation.IStringable")
}
