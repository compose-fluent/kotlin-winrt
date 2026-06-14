plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
}

description = "WinRT and WinUI authoring support for the Kotlin projection"

kotlin {
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
            implementation(projects.winrtRuntime)
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        // Authoring runtime contracts live in commonMain. Metadata/KotlinPoet are JVM-hosted
        // generator/compiler-tooling dependencies, not a separate JVM runtime implementation.
        jvmMain.dependencies {
            implementation(projects.winrtMetadata)
            implementation(libs.kotlinpoet)
        }

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.junit)
        }
    }
}
