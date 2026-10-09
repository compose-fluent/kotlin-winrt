pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-winrt-ide"
// Kotlin source-set output paths are initialized when its plugin is applied.
// Select SDK-specific directories before that boundary, including the FIR jar.
providers.gradleProperty("kotlinWinRT.ide.variant").orNull?.let { variant ->
    require(variant.matches(Regex("[a-z][a-z0-9-]{0,63}"))) { "Use a simple SDK variant name." }
    // Gradle remembers previous outputs per task and removes them when paths
    // change. A separate buildDirectory alone cannot preserve another SDK's
    // classes while its native tests are running.
    val cache = file(".gradle/variants/$variant").canonicalFile
    require(gradle.startParameter.projectCacheDir?.canonicalFile == cache) {
        "SDK variant $variant requires --project-cache-dir $cache."
    }
    gradle.beforeProject { layout.buildDirectory.set(layout.projectDirectory.dir("build/variants/$variant")) }
}
include(":ide-model")
project(":ide-model").projectDir = file("../../windows-toolkit-gradle-plugin/ide-model")
include(":fir-adapter")
// Reuse the tooling build's isolated metadata/runtime producers, without configuring applications.
includeBuild("../../windows-toolkit-gradle-plugin") {
    name = "winrt-toolchain"
    dependencySubstitution {
        substitute(module("io.github.compose-fluent:winrt-metadata")).using(project(":winrt-metadata"))
    }
}
