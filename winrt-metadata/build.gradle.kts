plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    id("build-convention")
    id("winrt.publish")
}

description = "WinMD metadata loading and model construction for the Kotlin WinRT projection"

dependencies {
    implementation(projects.winrtRuntime)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
