rootProject.name = "winrt-gradle-plugin"

// This build is included by the main build and reuses the same physical module
// directories. Keep its Gradle/Kotlin state exclusive to the included build so
// the two producers cannot invalidate each other's local state or outputs.
gradle.beforeProject {
    val projectSegments = project.path
        .trimStart(':')
        .split(':')
        .filter(String::isNotEmpty)
    val outputPath = listOf("build", "included-projects") + projectSegments
    layout.buildDirectory.set(rootDir.resolve(outputPath.joinToString("/")))
}

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
    ":winrt-authoring",
    ":winrt-generator",
    ":winrt-compiler-plugin",
    ":winrt-compiler-plugin:callsite-contract",
    ":winrt-compiler-plugin:callsite-lowering",
)

project(":winrt-runtime").projectDir = file("../winrt-runtime")
project(":winrt-metadata").projectDir = file("../winrt-metadata")
project(":winrt-authoring").projectDir = file("../winrt-authoring")
project(":winrt-generator").projectDir = file("../winrt-generator")
project(":winrt-compiler-plugin").projectDir = file("../winrt-compiler-plugin")
project(":winrt-compiler-plugin:callsite-contract").projectDir = file("../winrt-compiler-plugin/callsite-contract")
project(":winrt-compiler-plugin:callsite-lowering").projectDir = file("../winrt-compiler-plugin/callsite-lowering")
