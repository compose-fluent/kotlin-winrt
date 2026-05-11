plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}
