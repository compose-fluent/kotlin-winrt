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
    providers.gradleProperty("kotlinWinRt.samples.simpleMathWinmd").orNull?.let { winmd(it) }
    runtimeAsset(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.dll").asFile.absolutePath)
    type("Windows.Foundation.IStringable")
}
