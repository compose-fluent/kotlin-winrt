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
    type("Windows.Foundation.IStringable")
}
