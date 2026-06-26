plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
    id("io.github.composefluent.winrt")
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
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-ui-xaml", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

dependencies {
    commonMainImplementation(project(":winrt-projections:windows-sdk"))
}

winRT {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    excludeNamespace("Windows")
    namespace("Windows.UI.Xaml")
    excludeType("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
    excludeAdditionNamespace("Windows.UI.Xaml.Media.Animation")
}

fun projectionArtifactVersion(
    metadataVersion: String,
    kotlinWinRTVersion: String,
): String =
    if (kotlinWinRTVersion.endsWith("-SNAPSHOT")) {
        "$metadataVersion-kotlin-winrt-$kotlinWinRTVersion"
    } else {
        metadataVersion
    }
