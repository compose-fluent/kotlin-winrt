plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64()
    sourceSets.commonMain.dependencies {
        api(project(":winrt-projections:windows-sdk"))
    }
}

windows {
    packageReferences {
        windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false)
    }
}
