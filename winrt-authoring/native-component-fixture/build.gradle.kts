plugins {
    alias(libs.plugins.kotlinMultiplatform)
    id("io.github.composefluent.winrt")
}

description = "Kotlin/WinRT native authoring component validation fixture"

kotlin {
    mingwX64()
}

winRt {
    windowsSdk(generateProjection = true)
    type("Windows.Foundation.IClosable")
    type("Windows.Foundation.IStringable")
}

val verifyNativeAuthoringComponentFixture by tasks.registering(
    io.github.composefluent.winrt.gradle.VerifyWinRtNativeAuthoringComponentFixtureTask::class,
) {
    group = "verification"
    description = "Validates that a real authored mingwX64 component exports WinRT activation entry points."
    dependsOn("validateCompileKotlinMingwX64WinRtNativeAuthoringExports")
    dependsOn("generateWinRtIdentity")
    componentDll.set(layout.buildDirectory.file("bin/mingwX64/releaseShared/native_component_fixture.dll"))
    authoredWinmd.set(layout.buildDirectory.file("kotlin-winrt/native-authoring/compileKotlinMingwX64/kotlin-winrt-authoring/native-component-fixture.winmd"))
    authoredHostManifest.set(layout.buildDirectory.file("kotlin-winrt/native-authoring/compileKotlinMingwX64/kotlin-winrt-authoring/native-component-fixture.host.json"))
    identityFile.set(layout.buildDirectory.file("generated/kotlin-winrt/identity/kotlin-winrt.json"))
    runtimeClassNames.set(
        listOf(
            "sample.NativeClosableThing",
            "sample.NativeStringableThing",
        ),
    )
    expectedDllName.set("native_component_fixture.dll")
    forbiddenDllName.set("native-component-fixture.dll")
}
