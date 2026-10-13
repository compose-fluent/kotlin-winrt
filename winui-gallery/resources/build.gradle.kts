plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}
val galleryUsesWinUi2 = providers.gradleProperty("kotlinWinRT.gallery.winuiVersion").orElse("3").get() == "2"
if (galleryUsesWinUi2) layout.buildDirectory.set(layout.projectDirectory.dir("build/winui2"))

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64()
}

// The application owns SDK bindings; this library exports compiled dictionaries.
tasks.named<io.github.composefluent.windows.toolkit.gradle.GenerateWinRTProjectionsTask>("generateWinRTProjections") {
    emitProjectionSources.set(false)
}

windows {
    if (galleryUsesWinUi2) xaml.framework.set(io.github.composefluent.winrt.runtime.XamlFramework.Uwp)
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false, generateProjection = false)
        // These dictionaries need compiler metadata and GenXbf, but own no SDK
        // bindings or authored Kotlin types.
        nugetPackage(if (galleryUsesWinUi2) "Microsoft.UI.Xaml" else "Microsoft.WindowsAppSDK", if (galleryUsesWinUi2) "2.8.7" else "2.5.1") {
            generateProjection = false
        }
    }
}
