import org.gradle.api.tasks.ClasspathNormalizer
import io.github.composefluent.winrt.build.VerifyBinaryMarkerAbsentTask
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
}

description = "Kotlin/JVM and Kotlin/Native runtime for WinRT and WinUI projection"

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
        compilations.getByName("main") {
            cinterops.create("winrtString") {
                definitionFile.set(project.file("src/nativeInterop/cinterop/winrtString.def"))
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest {
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
    }
}

