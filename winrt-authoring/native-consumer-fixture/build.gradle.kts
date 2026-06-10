plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

description = "Kotlin/WinRT native authoring dependency staging validation fixture"

kotlin {
    mingwX64()

    sourceSets {
        commonMain.dependencies {
            implementation(project(":winrt-authoring:native-component-fixture"))
        }
    }
}

winRt {
    windowsSdk(generateProjection = true)
    type("Windows.Foundation.IClosable")
    type("Windows.Foundation.IStringable")
    application {
        generateProjectPri.set(false)
    }
}

val verifyNativeAuthoringConsumerFixture by tasks.registering(
    io.github.composefluent.winrt.gradle.VerifyWinRtNativeAuthoringConsumerFixtureTask::class,
) {
    group = "verification"
    description = "Validates staging of native authored dependency artifacts through identity metadata."
    dependsOn("stageWinRtRuntimeAssets")
    runtimeAssetsRoot.set(layout.buildDirectory.dir("kotlin-winrt/runtime-assets"))
    expectedDllName.set("native_component_fixture.dll")
    expectedWinmdName.set("native-component-fixture.winmd")
    expectedHostManifestName.set("native-component-fixture.host.json")
    runtimeClassNames.set(
        listOf(
            "sample.NativeClosableThing",
            "sample.NativeStringableThing",
        ),
    )
    forbiddenJarName.set("native-component-fixture.jar")
}
