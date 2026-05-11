rootProject.name = "winrt-gradle-plugin"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("../kotlin-winrt-build-convention")
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
    ":winrt-compiler-plugin",
)

project(":winrt-runtime").projectDir = file("../winrt-runtime")
project(":winrt-metadata").projectDir = file("../winrt-metadata")
project(":winrt-generator").projectDir = file("../winrt-generator")
project(":winrt-compiler-plugin").projectDir = file("../winrt-compiler-plugin")
