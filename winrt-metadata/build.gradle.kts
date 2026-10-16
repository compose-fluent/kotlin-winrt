plugins {
    `java-library`
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    id("build-convention")
    id("winrt.publish")
}

description = "WinMD metadata loading and model construction for the Kotlin WinRT projection"

dependencies {
    api(project(":winrt-runtime:contracts"))
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}
