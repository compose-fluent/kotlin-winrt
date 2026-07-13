import io.github.composefluent.winrt.gradle.GenerateWinRTProjectionsTask

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
    commonMainImplementation(providers.gradleProperty("kotlinWinRT.test.windowsSdkCoordinate").get())
    commonMainImplementation(providers.gradleProperty("kotlinWinRT.test.windowsUiXamlCoordinate").get())
    commonMainImplementation(providers.gradleProperty("kotlinWinRT.test.windowsAppSdkCoordinate").get())
}

tasks.named<GenerateWinRTProjectionsTask>("generateWinRTProjections") {
    generatorWorkerJvmArgs.set(
        listOf(
            "-Xmx512m",
            "-XX:+UseSerialGC",
            "-Dfile.encoding=UTF-8",
        ),
    )
}
