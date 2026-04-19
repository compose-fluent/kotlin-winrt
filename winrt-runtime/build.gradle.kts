plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    testImplementation(libs.junit)
}
