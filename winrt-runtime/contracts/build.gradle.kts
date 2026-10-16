plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("build-convention")
    id("winrt.publish")
}

// CsWinRT tooling gets GUID/value contracts from the BCL without loading its
// runtime. Keep the Kotlin equivalents under runtime ownership, in a dependency
// with no COM, platform initialization, authoring, or compiler plugin machinery.
description = "Platform-independent WinRT value and framework contracts"
base { archivesName.set("winrt-runtime-contracts") }
mavenPublishing { coordinates(artifactId = "winrt-runtime-contracts") }
kotlin {
    jvm()
    mingwX64()
    sourceSets.commonTest.dependencies { implementation(kotlin("test")) }
}
