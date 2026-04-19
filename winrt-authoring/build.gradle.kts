plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    testImplementation(libs.junit)
}
