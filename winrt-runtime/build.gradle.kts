plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvmToolchain(22)
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
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        jvmMain {
            kotlin.srcDirs("src/main/kotlin", "src/jvmMain/kotlin")
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
