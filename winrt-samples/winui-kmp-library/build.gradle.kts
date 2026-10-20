plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkVersion")
    .orElse("2.5.1")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64 {
        binaries {
            sharedLib {
                freeCompilerArgs += "-Xpartial-linkage-loglevel=error"
            }
        }
    }
    sourceSets {
        winuiMain {
            dependencies {
                api(project(":winrt-samples:winui-kmp-base-library"))
                api(project(":winrt-projections:windows-app-sdk"))
            }
        }
    }
}

windows {
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        packageReferences {
            windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false)
            nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion) {
                generateProjection = false
            }        }
    }
}
