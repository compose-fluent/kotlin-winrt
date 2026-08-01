plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
}

description = "Compile-time contract for statically planned WinRT projection call sites"

dependencies {
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}
