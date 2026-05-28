plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
}

description = "Kotlin compiler plugin for WinRT and WinUI projection support"

dependencies {
    implementation(projects.winrtAuthoring)
    implementation(projects.winrtMetadata)
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit)
}
