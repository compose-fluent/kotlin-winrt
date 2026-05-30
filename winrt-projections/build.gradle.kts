plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("io.github.composefluent.winrt")
}

dependencies {
    implementation(projects.winrtRuntime)
}

val projectionWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
    .orElse("1.8.260416003")
val projectionWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")
val projectionIncludeWinAppSdk = providers.gradleProperty("kotlinWinRt.projections.includeWinAppSdk")
    .map(String::toBooleanStrict)
    .orElse(false)

winRt {
    windowsSdk(projectionWindowsSdkVersion.get(), includeExtensions = false)
    if (projectionIncludeWinAppSdk.get()) projectionWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
    }

    namespace("Windows.Foundation")
    namespace("Windows.Foundation.Collections")
    namespace("Windows.Data.Json")
    namespace("Windows.System")
    namespace("Windows.ApplicationModel.DataTransfer")
    namespace("Windows.System.Display")
    namespace("Windows.UI.ViewManagement")
    namespace("Windows.UI.Xaml.Interop")
    if (projectionIncludeWinAppSdk.get()) {
        namespace("Microsoft.UI.Dispatching")
        namespace("Microsoft.UI.Windowing")
        namespace("Microsoft.UI.Xaml")
        namespace("Microsoft.UI.Xaml.Automation")
        namespace("Microsoft.UI.Xaml.Automation.Peers")
        namespace("Microsoft.UI.Xaml.Controls")
        namespace("Microsoft.UI.Xaml.Media")
    }
    namespace("SimpleMathComponent")
    winmd(
        providers.gradleProperty("kotlinWinRt.samples.simpleMathWinmd")
            .getOrElse(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.winmd").asFile.absolutePath),
    )
    runtimeAsset(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.dll").asFile.absolutePath)
    type("Windows.Foundation.IStringable")
}
