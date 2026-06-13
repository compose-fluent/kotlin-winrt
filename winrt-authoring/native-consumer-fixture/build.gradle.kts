plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

description = "Kotlin/WinRT native authoring dependency staging validation fixture"

kotlin {
    mingwX64 {
        binaries {
            executable {
                entryPoint = "sample.consumer.main"
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":winrt-authoring:native-component-fixture"))
        }
    }
}

winRt {
    windowsSdk(generateProjection = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3") {
        generateProjection = true
    }
    type("Windows.Foundation.IClosable")
    type("Windows.Foundation.IStringable")
    type("Windows.Data.Json.IJsonValue")
    type("Windows.Data.Json.JsonValue")
    type("Windows.Foundation.Collections.IPropertySet")
    type("Windows.Storage.Streams.IDataReader")
    type("Microsoft.UI.Xaml.Controls.Control")
    type("Microsoft.UI.Xaml.Controls.ContentControl")
    type("sample.NativeJsonValueThing")
    application {
        mainClass.set("sample.consumer.MainKt")
        generateProjectPri.set(false)
    }
}

tasks.named("runReleaseExecutableMingwX64") {
    dependsOn("stageWinRtRuntimeAssets")
}

val verifyNativeAuthoringConsumerFixture by tasks.registering(
    io.github.composefluent.winrt.gradle.VerifyWinRtNativeAuthoringConsumerFixtureTask::class,
) {
    group = "verification"
    description = "Validates staging and runtime activation of native authored dependency artifacts."
    dependsOn("stageWinRtRuntimeAssets")
    dependsOn("runReleaseExecutableMingwX64")
    runtimeAssetsRoot.set(layout.buildDirectory.dir("kotlin-winrt/runtime-assets"))
    expectedDllName.set("native_component_fixture.dll")
    expectedWinmdName.set("native-component-fixture.winmd")
    expectedHostManifestName.set("native-component-fixture.host.json")
    runtimeClassNames.set(
        listOf(
            "sample.NativeClosableThing",
            "sample.NativeStringableThing",
            "sample.NativeJsonValueThing",
            "sample.NativeDataReaderThing",
            "sample.NativePropertySetThing",
            "sample.NativeContentControlThing",
        ),
    )
    forbiddenJarName.set("native-component-fixture.jar")
}
