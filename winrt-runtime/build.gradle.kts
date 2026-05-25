plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
}

description = "Kotlin/JVM and Kotlin/Native runtime for WinRT and WinUI projection"

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
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.io.core)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmTest {
            kotlin.srcDirs("src/test/kotlin", "src/jvmTest/kotlin")
            dependencies {
                implementation(kotlin("test-junit"))
                implementation(libs.junit)
            }
        }
    }
}

// Native test execution is not part of the active mingwX64 baseline yet. Realize
// the link task during configuration so Kotlin Gradle Plugin metrics do not
// create it after project services are closed while storing the configuration cache.
tasks.named("linkDebugTestMingwX64") {
    enabled = false
}.get()
