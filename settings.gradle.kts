rootProject.name = "kotlin-winrt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("winrt-gradle-plugin")

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":winrt-runtime",
    ":winrt-metadata",
    ":winrt-generator",
    ":winrt-authoring",
    ":winrt-compiler-plugin",
    ":winrt-projections",
    ":winrt-samples",
    ":winrt-samples:winui-kmp-base-library",
    ":winrt-samples:winui-kmp-library",
    ":winrt-samples:winui-kmp-app",
)
