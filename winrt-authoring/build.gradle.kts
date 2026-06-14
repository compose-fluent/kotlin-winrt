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

        jvmTest.dependencies {
            implementation(kotlin("test-junit"))
            implementation(libs.junit)
        }
    }
}
