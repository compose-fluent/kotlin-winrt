import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.mavenPublish) apply false
}

val releaseTagRegex = Regex("v\\d+\\.\\d+\\.\\d+(-.*)?")

/**
 * Resolves the project version:
 * - Release: read git tag name from CI environment (`GITHUB_REF_TYPE=tag`, `GITHUB_REF_NAME=vX.Y.Z`)
 *   or explicit `-Pwinrt.releaseTag=vX.Y.Z`, then strip leading `v`.
 * - Otherwise: `<winrt.baseVersion>-SNAPSHOT`.
 */
fun resolveVersion() = providers
    .gradleProperty("winrt.baseVersion")
    .orElse("0.1.0")
    .zip(
        providers
            .environmentVariable("GITHUB_REF_TYPE")
            .zip(providers.environmentVariable("GITHUB_REF_NAME")) { refType, refName ->
                if (refType == "tag") refName else ""
            }
            .orElse(providers.gradleProperty("winrt.releaseTag").orElse("")),
    ) { baseVersion, releaseTag ->
        if (releaseTag.isNotBlank() && releaseTag.matches(releaseTagRegex)) {
            releaseTag.removePrefix("v")
        } else {
            "$baseVersion-SNAPSHOT"
        }
    }

val winrtVersion = resolveVersion().get()

allprojects {
    group = "io.github.compose-fluent"
    version = winrtVersion

    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        minHeapSize = "64m"
        maxHeapSize = "128m"
        jvmArgs("-XX:+UseSerialGC")
        systemProperty(
            "io.github.composefluent.winrt.enableProbe",
            providers.gradleProperty("io.github.composefluent.winrt.enableProbe").orNull ?: "false",
        )
        systemProperty(
            "io.github.composefluent.winrt.probeTarget",
            providers.gradleProperty("io.github.composefluent.winrt.probeTarget").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.probeMode",
            providers.gradleProperty("io.github.composefluent.winrt.probeMode").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.bootstrapDll",
            providers.systemProperty("io.github.composefluent.winrt.bootstrapDll").orNull ?: "",
        )
        systemProperty(
            "io.github.composefluent.winrt.windowsAppSdkRoot",
            providers.systemProperty("io.github.composefluent.winrt.windowsAppSdkRoot").orNull ?: "",
        )
    }
}

val validateWinRtGenerator by tasks.registering {
    group = "verification"
    description = "Runs the generator regression validation for the current WinRT slice."
    dependsOn(":winrt-generator:test")
}

val validateWinRtPluginGraph by tasks.registering {
    group = "verification"
    description = "Runs Gradle plugin graph validation, including TestKit and identity/resource wiring tests."
    dependsOn(validateWinRtGenerator)
    dependsOn(gradle.includedBuild("winrt-gradle-plugin").task(":test"))
}

val validateWinRtProjectionCompile by tasks.registering {
    group = "verification"
    description = "Compiles plugin-generated projection output after generator and plugin validation."
    dependsOn(validateWinRtPluginGraph)
    dependsOn(":validateWinRtFullWindowsSdkProjectionGate")
    dependsOn(":winrt-projections:auditGeneratedWinRtProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
    dependsOn(":winrt-projections:windows-sdk:compileKotlin")
    dependsOn(":winrt-projections:windows-app-sdk:compileKotlin")
}

val validateWinRtMingwProjectionGate by tasks.registering {
    group = "verification"
    description = "Runs the lightweight generated projection gate for mingwX64 without compiling full prebuilt projection artifacts."
    dependsOn(":winrt-projections:auditGeneratedWinRtProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
}

val validateWinRtFullWindowsSdkProjectionGate by tasks.registering {
    group = "verification"
    description = "Runs the root KMP generated projection gate against the full Windows SDK projection surface."
    dependsOn(":winrt-projections:auditGeneratedWinRtProjectionOutput")
    dependsOn(":winrt-projections:compileKotlinJvm")
    dependsOn(":winrt-projections:compileKotlinMingwX64")
}

val validateWinRtMingwParity by tasks.registering {
    group = "verification"
    description = "Runs the current mingwX64 parity gate across runtime contracts, compiler lowering, and full Windows SDK projections."
    dependsOn(":winrt-runtime:compileKotlinMetadata")
    dependsOn(":winrt-runtime:jvmTest")
    dependsOn(":winrt-runtime:compileTestKotlinMingwX64")
    dependsOn(":winrt-runtime:mingwX64Test")
    dependsOn(":winrt-compiler-plugin:test")
    dependsOn(validateWinRtFullWindowsSdkProjectionGate)
}

val validateWinRtNativeAuthoringFixture by tasks.registering {
    group = "verification"
    description = "Builds a real mingwX64 authored component DLL and validates native exports plus dependency staging."
    dependsOn(":winrt-authoring:native-component-fixture:verifyNativeAuthoringComponentFixture")
    dependsOn(":winrt-authoring:native-consumer-fixture:verifyNativeAuthoringConsumerFixture")
}

val validateWinRtSampleSmoke by tasks.registering {
    group = "verification"
    description = "Runs sample smoke checks after projection validation."
    dependsOn(validateWinRtProjectionCompile)
    dependsOn(":winrt-samples:check")
}

tasks.register("validateWinRtQueue16") {
    group = "verification"
    description = "Runs Queue 16 validation in reference-aligned order: generator, plugin graph, mingw parity, projections, samples."
    dependsOn(validateWinRtMingwParity)
    dependsOn(validateWinRtNativeAuthoringFixture)
    dependsOn(validateWinRtSampleSmoke)
}
