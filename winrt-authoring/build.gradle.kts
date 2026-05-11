plugins {
    alias(libs.plugins.kotlinJvm)
    id("winrt.kotlin-jvm")
    id("winrt.publish")
}

description = "WinRT and WinUI authoring support for the Kotlin projection"

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    testImplementation(libs.junit)
}
