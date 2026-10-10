plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
    // Shared dependencies, test classpaths and publication now follow the compiler matrix.
    id("winrt.compiler-plugin")
}
