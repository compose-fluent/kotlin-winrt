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
    implementation(projects.winrtMetadata)
    implementation(projects.winrtGenerator)
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
}

gradlePlugin {
    plugins {
        create("kotlinWinRt") {
            id = "io.github.kitectlab.winrt"
            implementationClass = "io.github.kitectlab.winrt.gradle.KotlinWinRtPlugin"
        }
        create("kotlinWinRtLibrary") {
            id = "io.github.kitectlab.winrt.library"
            implementationClass = "io.github.kitectlab.winrt.gradle.KotlinWinRtLibraryPlugin"
        }
        create("kotlinWinRtApplication") {
            id = "io.github.kitectlab.winrt.application"
            implementationClass = "io.github.kitectlab.winrt.gradle.KotlinWinRtApplicationPlugin"
        }
    }
}
