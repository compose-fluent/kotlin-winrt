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
    mainClass.set("io.github.kitectlab.winrt.projections.generator.KotlinProjectionGeneratorCliKt")
}
