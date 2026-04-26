plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt")
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
}

winRt {
    namespace("Windows.Data.Json")
    type("Windows.Foundation.IStringable")
}
