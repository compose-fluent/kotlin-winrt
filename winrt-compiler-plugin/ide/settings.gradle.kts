pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-winrt-ide"
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
