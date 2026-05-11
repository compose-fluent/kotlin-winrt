plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
