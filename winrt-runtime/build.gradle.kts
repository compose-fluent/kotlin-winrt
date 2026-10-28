import org.gradle.api.tasks.ClasspathNormalizer
import io.github.composefluent.winrt.build.VerifyBinaryMarkerAbsentTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.atomicfu)
    id("build-convention")
    id("winrt.publish")
}

description = "Kotlin/JVM and Kotlin/Native runtime for WinRT and WinUI projection"

// Runtime validation includes the value/signature foundation that it exports.
tasks.matching { it.name == "jvmTest" || it.name == "mingwX64Test" }.configureEach {
    dependsOn(":winrt-runtime:contracts:$name")
}

val callSiteIntegrationSources by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
dependencies.add(
    callSiteIntegrationSources.name,
    dependencies.project(mapOf("path" to ":winrt-generator", "configuration" to "callSiteIntegrationSources")),
)

val runtimeCallSiteLoweringClasspath = configurations.create("runtimeCallSiteLoweringClasspath") {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies.add(
    runtimeCallSiteLoweringClasspath.name,
    project(":winrt-compiler-plugin:callsite-lowering"),
)

// Keep the task-backed compiler plugin in the dedicated classpath properties. Exposing its
// resolved paths through freeCompilerArgs makes Kotlin IDE model import query the jar task too early.
tasks.withType<KotlinJvmCompile>().configureEach {
    inputs.files(runtimeCallSiteLoweringClasspath)
        .withPropertyName("runtimeCallSiteLoweringClasspath")
        .withNormalizer(ClasspathNormalizer::class.java)
    pluginClasspath.from(runtimeCallSiteLoweringClasspath)
}

tasks.withType<KotlinNativeCompile>().configureEach {
    inputs.files(runtimeCallSiteLoweringClasspath)
        .withPropertyName("runtimeCallSiteLoweringClasspath")
        .withNormalizer(ClasspathNormalizer::class.java)
}

// KGP replaces the Native task's compilerPluginClasspath after evaluating the build script.
// Extend its compilation configuration so that the runtime lowering survives that assignment.
configurations.matching { it.name.startsWith("kotlinCompilerPluginClasspathMingwX64") }.configureEach {
    extendsFrom(runtimeCallSiteLoweringClasspath)
    // Native plugin configurations are non-transitive, so include the lowering contract explicitly.
    dependencies.add(project.dependencies.project(mapOf("path" to ":winrt-compiler-plugin:callsite-contract")))
}

val verifyJvmRuntimeCallSiteLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that no runtime-owned WinRT call-site placeholder reaches JVM bytecode."
    dependsOn("compileKotlinJvm")
    val intrinsicClass = layout.buildDirectory.file(
        "classes/kotlin/jvm/main/io/github/composefluent/winrt/runtime/WinRTProjectionIntrinsic.class",
    )
    binaryArtifacts.from(intrinsicClass)
    markers.set(setOf("Lowered while building winrt-runtime"))
    artifactDescription.set("compiled JVM runtime call-site owner")
}

val verifyMingwX64RuntimeCallSiteLowering by tasks.registering(VerifyBinaryMarkerAbsentTask::class) {
    group = "verification"
    description = "Verifies that no runtime-owned WinRT call-site placeholder reaches the mingwX64 klib."
    dependsOn("compileKotlinMingwX64")
    val klibDirectory = layout.buildDirectory.dir("classes/kotlin/mingwX64/main/klib/winrt-runtime")
    binaryArtifacts.from(klibDirectory)
    markers.set(setOf("Lowered while building winrt-runtime"))
    requiredMarkers.set(setOf("kotlinWinRTNativeWideScalarResultThunk"))
    artifactDescription.set("compiled mingwX64 runtime klib")
}

tasks.matching { task -> task.name == "jvmJar" }.configureEach {
    dependsOn(verifyJvmRuntimeCallSiteLowering)
}

tasks.matching { task -> task.name == "mingwX64MainKlibrary" }.configureEach {
    dependsOn(verifyMingwX64RuntimeCallSiteLowering)
}

tasks.matching { task -> task.name == "mingwX64Klib" }.configureEach {
    dependsOn(verifyMingwX64RuntimeCallSiteLowering)
}

tasks.named("check") {
    dependsOn(verifyJvmRuntimeCallSiteLowering)
    dependsOn(verifyMingwX64RuntimeCallSiteLowering)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm {
        testRuns.named("test") {
            executionTask.configure {
                useJUnit()
            }
        }
    }
    mingwX64 {
        val runtimeMain = compilations.getByName("main")
        val policyProbe = compilations.create("developmentPolicyProbe") {
            associateWith(runtimeMain)
            defaultSourceSet.kotlin.srcDir("src/developmentPolicyProbe/kotlin")
        }
        binaries.executable("developmentPolicy", listOf(
            org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.DEBUG,
            org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType.RELEASE,
        )) {
            compilation = policyProbe
            entryPoint = "io.github.composefluent.winrt.runtime.policy.main"
            val executable = this
            runtimeMain.cinterops.all {
                val interop = tasks.named(interopProcessingTaskName)
                executable.linkTaskProvider.configure { libraries.from(interop) }
            }
            val mode = buildType.name.lowercase().replaceFirstChar(Char::uppercase)
            tasks.register<Exec>("verify${mode}XamlDevelopmentPolicy") {
                group = "verification"
                dependsOn(executable.linkTaskProvider)
                val session = layout.buildDirectory.dir("development-policy/$mode/session")
                environment("KOTLIN_WINRT_HOT_RELOAD_DIRECTORY", session.get().asFile.absolutePath)
                environment("KOTLIN_WINRT_XAML_PREVIEW", "1")
                environment("KOTLIN_WINRT_XAML_PREVIEW_THEME", "Dark")
                commandLine(executable.outputFile.absolutePath, "--kotlin-winrt-xaml-preview",
                    "--kotlin-winrt-xaml-preview-theme=Dark",
                    "--kotlin-winrt-hot-reload-directory=${session.get().asFile.absolutePath}")
            }
        }
        if (providers.gradleProperty("winrt.callsite.benchmark").orNull == "true") {
            binaries.getTest("DEBUG").apply {
                optimized = true
                debuggable = false
            }
        }
        compilations.getByName("main") {
            cinterops.create("winrtString") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/winrtString.def"))
            }
            cinterops.create("winrtCom") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/winrtCom.def"))
            }
        }
    }

    sourceSets {
        commonTest {
            kotlin.srcDir(callSiteIntegrationSources)
        }
        commonMain.dependencies {
            api(project(":winrt-runtime:contracts"))
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        mingwX64Test {
            // Friend tests use the internal platform weak handle, which aliases Native's
            // experimental WeakReference directly instead of allocating another wrapper.
            languageSettings.optIn("kotlin.experimental.ExperimentalNativeApi")
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
    }
}

