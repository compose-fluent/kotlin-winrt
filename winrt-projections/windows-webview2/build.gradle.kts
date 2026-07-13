import io.github.composefluent.winrt.build.projectionArtifactVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection")
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Microsoft WebView2 Core metadata surface."

base {
    archivesName.set("winrt-projections-windows-webview2")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWebView2Version = providers.gradleProperty("kotlinWinRT.projections.webView2Version")
    .orElse("1.0.3719.77")
val projectionWebView2ArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.webView2ArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWebView2Version.get(), rootProject.version.toString()) })

version = projectionWebView2ArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-webview2", version.toString())
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
    nugetPackage("Microsoft.Web.WebView2", projectionWebView2Version.get()) {
        generateProjection = true
    }
    excludeNamespace("Windows")
    namespace("Microsoft.Web.WebView2.Core")
}
