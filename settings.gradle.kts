rootProject.name = "kotlin-winrt"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
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
    ":kotlin-winrt-gradle-plugin",
    ":winrt-authoring",
    ":winrt-projections",
    ":winrt-samples",
)
