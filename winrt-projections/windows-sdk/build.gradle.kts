plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
    id("io.github.composefluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows SDK metadata surface."

base {
    archivesName.set("winrt-projections-windows-sdk")
}

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-sdk", version.toString())
}

dependencies {
    implementation(projects.winrtRuntime)
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")

winRt {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false)
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
