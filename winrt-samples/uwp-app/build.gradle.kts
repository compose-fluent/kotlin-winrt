import io.github.composefluent.windows.toolkit.gradle.WindowsApplicationModel
import io.github.composefluent.windows.toolkit.gradle.WindowsAppSdkDeployment

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

kotlin {
    jvmToolchain(25)
    jvm()
    mingwX64 { binaries.executable() }
}

windows {
    application {
        applicationModel = WindowsApplicationModel.UWP
        windowsAppSdkDeployment = WindowsAppSdkDeployment.None
        mainClass = "io.github.composefluent.winrt.samples.uwp.MainKt"
        executableBaseName = "kotlin-winrt-uwp"
        minWindowsVersion = "10.0.19041.0"
    }
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false)
        nugetPackage("Microsoft.UI.Xaml", "2.8.7")
        type("Microsoft.UI.Xaml.Controls.XamlControlsResources")
        type("Microsoft.UI.Xaml.Controls.NumberBox")
        type("Windows.UI.Xaml.Application")
        type("Windows.UI.Xaml.Window")
        type("Windows.UI.Xaml.Controls.Page")
        type("Windows.UI.Xaml.Controls.Button")
        type("Windows.UI.Xaml.Controls.TextBlock")
        type("Windows.UI.Xaml.Controls.StackPanel")
        type("Windows.UI.Xaml.Media.SolidColorBrush")
        type("Windows.Storage.ApplicationData")
        type("Windows.ApplicationModel.Package")
    }
}
