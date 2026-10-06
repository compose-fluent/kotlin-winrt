pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "kotlin-winrt-ide"
include(":ide-model")
project(":ide-model").projectDir = file("../../windows-toolkit-gradle-plugin/ide-model")
