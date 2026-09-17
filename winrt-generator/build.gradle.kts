import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
    application
}

description = "Kotlin source generator for WinRT and WinUI projection bindings"

dependencies {
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(projects.winrtCompilerPlugin.callsiteContract)
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

// The fixture is rendered by the real generator and compiled by both runtime test targets.
// A separate task makes source production explicit, including after a clean/cache restore.
val generateCallSiteIntegrationSources by tasks.registering(Test::class) {
    group = "verification"
    description = "Generates actual projection source for JVM and Native ABI integration tests."
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    filter.includeTestsMatching("*.KotlinScalarCallSiteSourceTest.scalar_conversions_and_storage_are_emitted_as_source")
    filter.includeTestsMatching("*.KotlinGuidCallSiteSourceTest.guid_input_and_return_use_generated_marshaling")
    filter.includeTestsMatching("*.KotlinHStringCallSiteSourceTest.hstring_input_and_return_use_generated_marshaling")
    filter.includeTestsMatching("*.KotlinSharedCallSiteInputTest.generated_shared_inputs_preserve_null_disposal_and_enum_bits")
    val outputDirectory = layout.buildDirectory.dir("generated/callsite-integration")
    outputs.dir(outputDirectory).withPropertyName("generatedCallSiteSources")
    systemProperty("winrt.callsite.integration.output", outputDirectory.get().asFile.absolutePath)
    systemProperty("winrt.callsite.benchmark", providers.gradleProperty("winrt.callsite.benchmark").getOrElse("false"))
}

val callSiteIntegrationSources by configurations.creating {
    isCanBeConsumed = true
    isCanBeResolved = false
}
artifacts.add(callSiteIntegrationSources.name, layout.buildDirectory.dir("generated/callsite-integration")) {
    builtBy(generateCallSiteIntegrationSources)
}
