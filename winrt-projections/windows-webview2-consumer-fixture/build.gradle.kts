plugins {
    kotlin("multiplatform") version "2.4.0"
    id("io.github.compose-fluent.winrt")
}

repositories {
    maven {
        url = uri(providers.gradleProperty("kotlinWinRT.test.repository").get())
    }
    mavenCentral()
}

kotlin {
    jvm()
    mingwX64()
}

dependencies {
    val projectionCoordinate = providers.gradleProperty("kotlinWinRT.test.coordinate").get()
    commonMainImplementation(projectionCoordinate)
    add("kotlinWinRTLibraryDependencyIdentity", projectionCoordinate)
}
