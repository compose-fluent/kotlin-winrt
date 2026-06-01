import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    `java-gradle-plugin`
    id("build-convention")
    id("winrt.publish")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

description = "Gradle plugin for Kotlin WinRT and WinUI projection generation, compiler wiring, and packaging"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation(gradleApi())
    implementation(projects.winrtAuthoring)
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(projects.winrtGenerator)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlin.gradle.plugin)
    compileOnly(projects.winrtCompilerPlugin)
    runtimeOnly(projects.winrtCompilerPlugin)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    minHeapSize = "64m"
    maxHeapSize = "128m"
    jvmArgs("-XX:+UseSerialGC")
}

gradlePlugin {
    plugins {
        create("kotlinWinRt") {
            id = "io.github.composefluent.winrt"
            implementationClass = "io.github.composefluent.winrt.gradle.KotlinWinRtPlugin"
        }
    }
}
