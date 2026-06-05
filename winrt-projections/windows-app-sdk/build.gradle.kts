plugins {
    alias(libs.plugins.kotlinJvm)
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
    .orElse(projectionWindowsSdkVersion)
val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.projections.windowsAppSdkVersion")
    .orElse("2.1.3")
val projectionWindowsAppSdkArtifactVersion = providers.gradleProperty("kotlinWinRt.projections.windowsAppSdkArtifactVersion")
    .orElse(projectionWindowsAppSdkVersion)

version = projectionWindowsAppSdkArtifactVersion.get()
project(":winrt-projections:windows-sdk").version = projectionWindowsSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-app-sdk", version.toString())
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(project(":winrt-projections:windows-sdk"))
}

winRt {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false)
    nugetPackage("Microsoft.WindowsAppSDK") {
        version.set(projectionWindowsAppSdkVersion.get())
        generateProjection.set(true)
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
