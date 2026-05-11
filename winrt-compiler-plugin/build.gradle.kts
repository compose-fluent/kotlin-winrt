plugins {
    id("winrt.kotlin-jvm")
    id("winrt.publish")
}

description = "Kotlin compiler plugin for WinRT and WinUI projection support"

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit)
}
