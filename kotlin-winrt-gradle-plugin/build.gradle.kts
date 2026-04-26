plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(22))
    }
}

dependencies {
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
