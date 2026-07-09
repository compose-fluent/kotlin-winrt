import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata

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
    compileOnly(gradleApi())
    implementation(projects.winrtAuthoring)
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(projects.winrtGenerator)
    implementation(projects.winrtCompilerPlugin)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlin.gradle.plugin)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    minHeapSize = "64m"
    maxHeapSize = "128m"
    jvmArgs("-XX:+UseSerialGC")
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.setFrom(
        tasks.named("jar"),
        configurations.named("runtimeClasspath").map { runtimeClasspath ->
            runtimeClasspath.filter { file ->
                file.isFile && file.extension.equals("jar", ignoreCase = true)
            }
        },
    )
}

gradlePlugin {
    plugins {
        create("kotlinWinRT") {
            id = "io.github.compose-fluent.winrt"
            implementationClass = "io.github.composefluent.winrt.gradle.KotlinWinRTPlugin"
        }
    }
}
