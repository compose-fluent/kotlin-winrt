import io.github.composefluent.winrt.build.projectionArtifactVersion

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("winrt.prebuilt-projection")
    id("io.github.compose-fluent.winrt")
}

description = "Prebuilt Kotlin/WinRT projection for the Windows App SDK and WinUI metadata surface."

base {
    archivesName.set("winrt-projections-windows-app-sdk")
}

val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkVersion")
    .orElse("2.1.3")
val projectionWindowsAppSdkArtifactVersion = providers.gradleProperty("kotlinWinRT.projections.windowsAppSdkArtifactVersion")
    .orElse(providers.provider { projectionArtifactVersion(projectionWindowsAppSdkVersion.get(), rootProject.version.toString()) })

version = projectionWindowsAppSdkArtifactVersion.get()

mavenPublishing {
    coordinates(group.toString(), "winrt-projections-windows-app-sdk", version.toString())
}

kotlin {
    jvm()
    mingwX64()
}

dependencies {
    commonMainCompileOnly(project(":winrt-projections:windows-sdk"))
    commonMainApi(project(":winrt-projections:windows-webview2"))
}

winRT {
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
    excludeNamespace("Microsoft.Web.WebView2")
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
    // CsWinRT's standalone test projection consumes the official Microsoft.WinUI assembly for this
    // control. Kotlin owns its WinUI projection, so emit the control while keeping WebView2 Core in
    // the separately published windows-webview2 artifact.
    excludeType("Microsoft.UI.Xaml.Controls.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.IWebView")
    excludeType("Microsoft.UI.Xaml.Automation.Peers.WebView")
    excludeAdditionNamespace("Windows.UI.Xaml.Media.Animation")
}
