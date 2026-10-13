plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64()
}

// This library owns plain Gallery models, not SDK projection bindings.
tasks.named<io.github.composefluent.windows.toolkit.gradle.GenerateWinRTProjectionsTask>("generateWinRTProjections") {
    emitProjectionSources.set(false)
}

windows {
    packageReferences {
        // Compiler schema maps Kotlin List to WinRT collection metadata. This
        // does not generate SDK bindings or export the models as components.
        windowsSdk(providers.gradleProperty("kotlinWinRT.samples.windowsSdkVersion")
            .orElse("10.0.26100.0").get(), generateProjection = false)
    }
}
