import io.github.composefluent.winrt.gradle.GenerateWinRtProjectionsTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

tasks.named<GenerateWinRtProjectionsTask>("generateWinRtProjections") {
    sourceRoots.setFrom(project.file("src/winuiMain/kotlin"))
}

val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    sourceSets {
        val winuiMain by creating {
            dependsOn(commonMain.get())
        }
        named("winuiJvmMain") {
            dependsOn(winuiMain)
        }
    }
}

winRt {
    windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    type("Windows.Foundation.IStringable")
    type("Windows.Foundation.Uri")
    type("Windows.System.Launcher")
}
