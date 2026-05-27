import io.github.composefluent.winrt.gradle.GenerateWinRtProjectionsTask

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

tasks.named<GenerateWinRtProjectionsTask>("generateWinRtProjections") {
    sourceRoots.setFrom(project.file("src/winuiMain/kotlin"))
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")
    .orElse("1.8.260416003")
val sampleWindowsSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsSdkVersion")
    .orElse("10.0.26100.0")

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":winrt-runtime"))
                implementation(project(":winrt-authoring"))
                api(project(":winrt-samples:winui-kmp-base-library"))
            }
        }
        val winuiMain by creating {
            dependsOn(commonMain.get())
        }
        named("winuiJvmMain") {
            dependsOn(winuiMain)
        }
    }
}

winRt {
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        windowsSdk(sampleWindowsSdkVersion.get(), includeExtensions = false)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
        type("Windows.Foundation.IStringable")
        type("Windows.ApplicationModel.DataTransfer.Clipboard")
        type("Windows.ApplicationModel.DataTransfer.DataPackage")
        type("Windows.ApplicationModel.DataTransfer.DataPackageView")
        type("Windows.ApplicationModel.DataTransfer.StandardDataFormats")
        type("Windows.System.Display.DisplayRequest")
        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.DependencyProperty")
        type("Microsoft.UI.Xaml.FrameworkElement")
        type("Microsoft.UI.Xaml.HorizontalAlignment")
        type("Microsoft.UI.Xaml.LaunchActivatedEventArgs")
        type("Microsoft.UI.Xaml.RoutedEventArgs")
        type("Microsoft.UI.Xaml.RoutedEventHandler")
        type("Microsoft.UI.Xaml.ResourceDictionary")
        type("Microsoft.UI.Xaml.Thickness")
        type("Microsoft.UI.Xaml.UIElement")
        type("Microsoft.UI.Xaml.VerticalAlignment")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.WindowActivatedEventArgs")
        type("Microsoft.UI.Xaml.WindowActivationState")
        type("Microsoft.UI.Xaml.Automation.AutomationProperties")
        type("Microsoft.UI.Xaml.Automation.Peers.AutomationPeer")
        type("Microsoft.UI.Xaml.Automation.Peers.FrameworkElementAutomationPeer")
        type("Microsoft.UI.Xaml.Automation.Peers.AccessibilityView")
        type("Microsoft.UI.Xaml.Media.DesktopAcrylicBackdrop")
        type("Microsoft.UI.Xaml.Media.MicaBackdrop")
        type("Microsoft.UI.Xaml.Media.RectangleGeometry")
        type("Microsoft.UI.Xaml.Media.SystemBackdrop")
        type("Microsoft.UI.Dispatching.DispatcherQueue")
        type("Microsoft.UI.Dispatching.DispatcherQueueTimer")
        type("Microsoft.UI.Dispatching.IDispatcherQueue2")
        type("Microsoft.UI.Windowing.AppWindow")
        type("Microsoft.UI.Windowing.AppWindowClosingEventArgs")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.Canvas")
        type("Microsoft.UI.Xaml.Controls.Control")
        type("Microsoft.UI.Xaml.Controls.ContentControl")
        type("Microsoft.UI.Xaml.Controls.Panel")
        type("Microsoft.UI.Xaml.Controls.TextBox")
        type("Microsoft.UI.Xaml.Controls.ToggleSwitch")
        type("Microsoft.UI.Xaml.Controls.UIElementCollection")
        type("Microsoft.UI.Xaml.Controls.XamlControlsResources")
        type("Windows.Foundation.Rect")
        type("Windows.Foundation.Size")
        type("Windows.UI.ViewManagement.UISettings")
        type("Windows.UI.Xaml.Interop.Type")
        type("Windows.UI.Xaml.Interop.NotifyCollectionChangedAction")
    }
}
