import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
}

allprojects {
    group = "io.github.kitectlab.winrt"
    version = "0.1.0-SNAPSHOT"

    tasks.withType<Test>().configureEach {
        maxParallelForks = 1
        minHeapSize = "64m"
        maxHeapSize = "128m"
        jvmArgs("-XX:+UseSerialGC")
        systemProperty(
            "io.github.kitectlab.winrt.enableProbe",
            providers.gradleProperty("io.github.kitectlab.winrt.enableProbe").orNull ?: "false",
        )
        systemProperty(
            "io.github.kitectlab.winrt.probeTarget",
            providers.gradleProperty("io.github.kitectlab.winrt.probeTarget").orNull ?: "",
        )
        systemProperty(
            "io.github.kitectlab.winrt.probeMode",
            providers.gradleProperty("io.github.kitectlab.winrt.probeMode").orNull ?: "",
        )
        systemProperty(
            "io.github.kitectlab.winrt.bootstrapDll",
            providers.systemProperty("io.github.kitectlab.winrt.bootstrapDll").orNull ?: "",
        )
        systemProperty(
            "io.github.kitectlab.winrt.windowsAppSdkRoot",
            providers.systemProperty("io.github.kitectlab.winrt.windowsAppSdkRoot").orNull ?: "",
        )
    }
}
