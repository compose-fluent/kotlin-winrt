plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
    // Shared dependencies and publication now follow the compiler matrix.
    id("winrt.compiler-plugin")
}
