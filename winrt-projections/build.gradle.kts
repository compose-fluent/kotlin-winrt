plugins {
    id("winrt.kotlin-jvm")
    id("io.github.composefluent.winrt")
}

dependencies {
    implementation(projects.winrtRuntime)
}

winRt {
    namespace("Windows.Data.Json")
    namespace("SimpleMathComponent")
    winmd(
        providers.gradleProperty("kotlinWinRt.samples.simpleMathWinmd")
            .getOrElse(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.winmd").asFile.absolutePath),
    )
    runtimeAsset(layout.projectDirectory.file("src/main/winrt/SimpleMathComponent.dll").asFile.absolutePath)
    type("Windows.Foundation.IStringable")
}
