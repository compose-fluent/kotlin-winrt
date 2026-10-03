plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.windows-toolkit")
}

kotlin {
    jvmToolchain(25)
    jvm("winuiJvm")
    mingwX64()
}

windows {
    packageReferences {
        windowsSdk("10.0.26100.0", includeExtensions = false, generateProjection = false)
        // These dictionaries need compiler metadata and GenXbf, but own no SDK
        // bindings or authored Kotlin types.
        nugetPackage("Microsoft.WindowsAppSDK", "2.5.1") {
            generateProjection = false
        }
    }
}
