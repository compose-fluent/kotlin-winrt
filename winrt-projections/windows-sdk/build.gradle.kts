import io.github.composefluent.winrt.build.projectionArtifactVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection")
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows SDK metadata surface."

base {
    archivesName.set("winrt-projections-windows-sdk")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-sdk", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    namespace("Windows")
    excludeNamespace("Windows.UI.Xaml")
    excludeNamespace("Windows.ApplicationModel.Store.Preview")
    excludeType("Windows.UI.Colors")
    excludeType("Windows.UI.IColors")
    excludeType("Windows.UI.ColorHelper")
    excludeType("Windows.UI.IColorHelper")
    excludeType("Windows.UI.IColorHelperStatics")
    excludeType("Windows.UI.IColorHelperStatics2")
    type("Windows.UI.Text.FontStretch")
    type("Windows.UI.Text.FontStyle")
    type("Windows.UI.Text.FontWeight")
    type("Windows.UI.Text.UnderlineType")
    type("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
}
