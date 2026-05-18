plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
}

description = "WinMD metadata loading and model construction for the Kotlin WinRT projection"

dependencies {
    implementation(projects.winrtRuntime)
    testImplementation(libs.junit)
}
