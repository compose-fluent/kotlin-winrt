import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.mavenPublish) apply false
}

/**
 * Resolves the project version:
 * - If HEAD is on an exact git tag matching `v<semver>`, the version is the tag without the "v" prefix.
 * - Otherwise, the version is `<winrt.baseVersion>-SNAPSHOT`.
 *
 * This allows CI release builds (triggered by pushing a version tag) to publish non-SNAPSHOT
 * artifacts, while all other builds publish SNAPSHOTs.
 */
fun resolveVersion(): String {
    val baseVersion = providers.gradleProperty("winrt.baseVersion").orNull ?: "0.1.0"
    return try {
        val process = ProcessBuilder("git", "describe", "--tags", "--exact-match", "HEAD")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
        val tag = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0 && tag.matches(Regex("v\\d+\\.\\d+\\.\\d+(-.*)?+"))) {
            tag.removePrefix("v")
        } else {
            "$baseVersion-SNAPSHOT"
        }
    } catch (_: Exception) {
        "$baseVersion-SNAPSHOT"
    }
}

val winRtVersion = resolveVersion()

allprojects {
    group = "io.github.composefluent.winrt"
    version = winRtVersion

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
    dependsOn(":winrt-projections:compileKotlin")
}

val validateWinRtSampleSmoke by tasks.registering {
    group = "verification"
    description = "Runs sample smoke checks after projection validation."
    dependsOn(validateWinRtProjectionCompile)
    dependsOn(":winrt-samples:check")
}

tasks.register("validateWinRtQueue16") {
    group = "verification"
    description = "Runs Queue 16 validation in cswinrt-aligned order: generator, plugin graph, projections, samples."
    dependsOn(validateWinRtSampleSmoke)
}
