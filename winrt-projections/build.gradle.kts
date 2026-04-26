plugins {
    alias(libs.plugins.kotlinJvm)
    id("io.github.kitectlab.winrt.library")
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
}

kotlinWinRt {
    type("Windows.Foundation.IStringable")
}
