plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtProjections)
    testImplementation(libs.junit)
}

application {
    mainClass = "io.github.kitectlab.winrt.samples.MainKt"
}
