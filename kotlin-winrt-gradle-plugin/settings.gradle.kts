rootProject.name = "kotlin-winrt-gradle-plugin"

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
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

include(
    ":winrt-runtime",
    ":winrt-metadata",
    ":winrt-generator",
)

project(":winrt-runtime").projectDir = file("../winrt-runtime")
project(":winrt-metadata").projectDir = file("../winrt-metadata")
project(":winrt-generator").projectDir = file("../winrt-generator")
