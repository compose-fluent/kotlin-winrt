import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinJvm)
    id("build-convention")
    id("winrt.publish")
}

description = "Kotlin compiler plugin for WinRT and WinUI projection support"

val callSiteTestPluginClasspath by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
dependencies.add(callSiteTestPluginClasspath.name, project.childProjects.getValue("callsite-lowering"))
tasks.withType<Test>().configureEach {
    val fullPluginJar = tasks.named<Jar>("jar").flatMap { it.archiveFile }
    inputs.file(fullPluginJar).withPropertyName("fullPluginJar")
    inputs.files(callSiteTestPluginClasspath).withPropertyName("callSiteTestPluginClasspath")
    doFirst {
        systemProperty("winrt.test.callsitePluginClasspath", callSiteTestPluginClasspath.asPath)
        systemProperty("winrt.test.fullPluginJar", fullPluginJar.get().asFile.absolutePath)
    }
}

dependencies {
    implementation(project.childProjects.getValue("callsite-contract"))
    implementation(project.childProjects.getValue("callsite-lowering"))
    implementation(projects.winrtAuthoring)
    implementation(projects.winrtRuntime)
    implementation(projects.winrtMetadata)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit)
}
