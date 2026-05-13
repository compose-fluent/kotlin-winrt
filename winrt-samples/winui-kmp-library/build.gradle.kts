plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

val sampleWindowsAppSdkVersion = providers.gradleProperty("kotlinWinRt.samples.windowsAppSdkVersion")

kotlin {
    jvmToolchain(22)
    jvm("winuiJvm")
    sourceSets {
        commonMain {
            dependencies {
                implementation(project(":winrt-runtime"))
                implementation(project(":winrt-authoring"))
            }
        }
    }
}

winRt {
    sampleWindowsAppSdkVersion.orNull?.let { windowsAppSdkVersion ->
        windowsSdk(includeExtensions = true)
        nugetPackage("Microsoft.WindowsAppSDK", windowsAppSdkVersion)
        type("Microsoft.UI.Xaml.Application")
        type("Microsoft.UI.Xaml.LaunchActivatedEventArgs")
        type("Microsoft.UI.Xaml.Window")
        type("Microsoft.UI.Xaml.Controls.Button")
        type("Microsoft.UI.Xaml.Controls.ContentControl")
    }
}
