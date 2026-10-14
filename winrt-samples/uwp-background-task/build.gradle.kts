import io.github.composefluent.winrt.runtime.XamlFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

// Native component loaded by Windows backgroundTaskHost.exe. The JVM UWP
// AppContainer startup limitation also applies to a JVM background component.
kotlin { mingwX64 { binaries.sharedLib() } }

windows {
    xaml { framework = XamlFramework.UWP }
    packageReferences {
        windowsSdk("10.0.26100.0", generateProjection = true)
        type("Windows.ApplicationModel.Background.IBackgroundTask")
        type("Windows.Storage.ApplicationData")
    }
}
