plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.compose-fluent.winrt")
}

description = "Kotlin/WinRT native authoring component validation fixture"

kotlin {
    mingwX64 {
        binaries {
            sharedLib()
        }
    }
}

winRT {
    windowsSdk(generateProjection = true)
    nugetPackage("Microsoft.WindowsAppSDK", "2.1.3") {
        generateProjection = true
    }
    type("Windows.Foundation.IClosable")
    type("Windows.Foundation.IStringable")
    type("Windows.Foundation.Collections.IPropertySet")
    type("Windows.Data.Json.IJsonValue")
    type("Windows.Storage.Streams.IDataReader")
    type("Microsoft.UI.Xaml.Controls.Control")
    type("Microsoft.UI.Xaml.Controls.ContentControl")
}

val verifyNativeAuthoringComponentFixture by tasks.registering(
    io.github.composefluent.winrt.gradle.VerifyWinRTNativeAuthoringComponentFixtureTask::class,
) {
    group = "verification"
    description = "Validates that a real authored mingwX64 component exports WinRT activation entry points."
    dependsOn("validateCompileKotlinMingwX64WinRTNativeAuthoringExports")
    dependsOn("generateWinRTIdentity")
    componentDll.set(layout.buildDirectory.file("bin/mingwX64/releaseShared/native_component_fixture.dll"))
    authoredWinmd.set(layout.buildDirectory.file("kotlin-winrt/native-authoring/compileKotlinMingwX64/kotlin-winrt-authoring/native-component-fixture.winmd"))
    authoredHostManifest.set(layout.buildDirectory.file("kotlin-winrt/native-authoring/compileKotlinMingwX64/kotlin-winrt-authoring/native-component-fixture.host.json"))
    identityFile.set(layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
    activationFactoryPlanSource.set(
        layout.buildDirectory.file("generated/kotlin-winrt/src/commonMain/kotlin/io/github/composefluent/winrt/projections/support/WinRTAuthoringActivationFactoryPlan.kt"),
    )
    serverActivationFactoriesSource.set(
        layout.buildDirectory.file("generated/kotlin-winrt/src/commonMain/kotlin/io/github/composefluent/winrt/projections/support/WinRTAuthoringServerActivationFactories_native_component_fixture_dll.kt"),
    )
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
    expectedDllName.set("native_component_fixture.dll")
    forbiddenDllName.set("native-component-fixture.dll")
}
