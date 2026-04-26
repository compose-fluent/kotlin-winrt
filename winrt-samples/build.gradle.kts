plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt.application")
    application
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtProjections)
    compileOnly(projects.winrtRuntime)
    runtimeOnly(projects.winrtRuntime)
    testImplementation(projects.winrtRuntime)
    testImplementation(libs.junit)
}

kotlinWinRt {
    type("Windows.Foundation.IStringable")
}

application {
    mainClass = "io.github.kitectlab.winrt.samples.MainKt"
}
