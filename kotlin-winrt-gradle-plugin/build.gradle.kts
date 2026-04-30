import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    `java-gradle-plugin`
}

apply(plugin = "org.jetbrains.kotlin.jvm")

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

dependencies {
    implementation(gradleApi())
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(projects.winrtGenerator)
    implementation(libs.kotlinpoet)
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
            id = "io.github.kitectlab.winrt"
            implementationClass = "io.github.kitectlab.winrt.gradle.KotlinWinRtPlugin"
        }
    }
}
