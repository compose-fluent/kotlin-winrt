import io.github.composefluent.winrt.build.projectionArtifactVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection")
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows.UI.Xaml metadata surface."

base {
    archivesName.set("winrt-projections-windows-ui-xaml")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-ui-xaml", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

dependencies {
    commonMainCompileOnly(project(":winrt-projections:windows-sdk"))
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    namespace("Windows.UI.Xaml")
    excludeNamespace("Windows.UI.Xaml.Controls.Maps")
    excludeType("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
    excludeAdditionNamespace("Windows.UI.Xaml.Media.Animation")
}
