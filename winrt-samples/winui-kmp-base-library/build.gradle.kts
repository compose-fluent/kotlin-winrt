import io.github.composefluent.windows.toolkit.gradle.GenerateWinRTProjectionsTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

tasks.named<GenerateWinRTProjectionsTask>("generateWinRTProjections") {
    sourceRoots.setFrom(project.file("src/winuiMain/kotlin"))
}

val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64()
}

windows {
    packageReferences {
        windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
        type("Windows.Foundation.IStringable")
        type("Windows.Foundation.Uri")
        type("Windows.System.Launcher")
    }
}
