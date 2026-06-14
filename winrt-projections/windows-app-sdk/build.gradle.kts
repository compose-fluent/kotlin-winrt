plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
    id("io.github.composefluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows App SDK and WinUI metadata surface."

base {
    archivesName.set("winrt-projections-windows-app-sdk")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsSdkArtifactVersion = providers.gradleProperty("kotlinWinRt.projections.windowsSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsSdkVersion.get(), rootProject.version.toString()) })
val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.projections.windowsAppSdkVersion")
    .orElse("2.1.3")
val projectionWindowsAppSdkArtifactVersion = providers.gradleProperty("kotlinWinRt.projections.windowsAppSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsAppSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsAppSdkArtifactVersion.get()
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-app-sdk", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

dependencies {
    commonMainImplementation(project(":winrt-projections:windows-sdk"))
}

winRt {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false, generateProjection = true)
    nugetPackage("Microsoft.WindowsAppSDK", projectionWindowsAppSdkVersion.get()) {
        generateProjection = true
    }
    excludeNamespace("Windows")
    excludeNamespace("Microsoft.Windows.Internal")
    excludeNamespace("Microsoft.Windows.AI.GenerativeInternal")
    excludeNamespace("Microsoft.Windows.Management.Deployment")
    excludeNamespace("Microsoft.Graphics.ImagingInternal")
    excludeNamespace("Microsoft.Graphics.Internal.Imaging")
    excludeNamespace("Microsoft.UI.Composition.SystemBackdrops")
    namespace("Microsoft")
    type("Windows.UI.Xaml.Interop.Type")
    type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
    type("Windows.UI.Xaml.Markup.ContentPropertyAttribute")
    type("Windows.UI.Xaml.StyleTypedPropertyAttribute")
    type("Windows.UI.Xaml.TemplatePartAttribute")
    type("Windows.UI.Xaml.TemplateVisualStateAttribute")
    type("Windows.UI.Xaml.Data.BindableAttribute")
    type("Windows.UI.Xaml.Markup.FullXamlMetadataProviderAttribute")
    type("Windows.UI.Xaml.Markup.MarkupExtensionReturnTypeAttribute")
    type("Windows.UI.Xaml.Media.Animation.ConditionallyIndependentlyAnimatableAttribute")
    type("Windows.UI.Xaml.Media.Animation.IndependentlyAnimatableAttribute")
    excludeType("Microsoft.UI.Xaml.Controls.WebView2")
    excludeType("Microsoft.UI.Xaml.Controls.IWebView2")
    excludeType("Microsoft.UI.Xaml.Controls.IWebView2Factory")
    excludeType("Microsoft.UI.Xaml.Controls.IWebView2Statics")
    excludeType("Microsoft.UI.Xaml.Controls.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.WebView2AutomationPeer")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.IWebView2AutomationPeer")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.IWebView2AutomationPeerFactory")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.WebView")
    excludeAdditionNamespace("Windows.UI.Xaml.Media.Animation")
}

fun projectionArtifactVersion(
    metadataVersion: String,
    kotlinWinRtVersion: String,
): String =
    if (kotlinWinRtVersion.endsWith("-SNAPSHOT")) {
        "$metadataVersion-kotlin-winrt-$kotlinWinRtVersion"
    } else {
        metadataVersion
    }
