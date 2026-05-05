import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    application
}

kotlin {
    jvmToolchain(22)
}

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(libs.kotlinpoet)
    testImplementation(libs.junit)
}

application {
    mainClass.set("io.github.composefluent.winrt.projections.generator.KotlinProjectionGeneratorCliKt")
}

tasks.withType<Test>().configureEach {
    minHeapSize = "128m"
    maxHeapSize = "768m"
    jvmArgs(
        "-XX:+UseSerialGC",
        "-XX:TieredStopAtLevel=1",
        "-XX:CICompilerCount=1",
        "-XX:HeapBaseMinAddress=8g",
    )
}
