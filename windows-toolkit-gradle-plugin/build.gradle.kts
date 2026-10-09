import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    `java-gradle-plugin`
    id("build-convention")
    id("winrt.publish")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

description = "Kotlin Windows toolkit Gradle plugin for WinRT projections, NuGet references, and Windows application packaging"

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
    implementation(libs.kotlinx.serialization.json)
    // Supply KGP for toolkit-only builds, but let a consumer's explicit version win,
    // including older unsupported versions which must receive our own diagnostic.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin") {
        version { prefer(libs.versions.kotlin.get()) }
    }
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
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

tasks.named<ProcessResources>("processResources") {
    from("../winrt-compiler-plugin/compiler-versions.properties") {
        into("kotlin-winrt")
    }
}

// Resolve the real published plugin graph: TestKit's injected classloader otherwise
// pins KGP or cannot see the consumer's KGP. Keep every publication under build/.
val compilerCompatibilityRepository = layout.buildDirectory.dir("compiler-compatibility-repository")
val compilerCompatibilityProjects = listOf(
    project,
    project(":winrt-runtime"), project(":winrt-authoring"), project(":winrt-metadata"), project(":winrt-generator"),
    project(":winrt-compiler-plugin"), project(":winrt-compiler-plugin:compiler-kotlin-2-4-20"),
    project(":winrt-compiler-plugin:callsite-contract"), project(":winrt-compiler-plugin:callsite-lowering"),
    project(":winrt-compiler-plugin:callsite-lowering:lowering-kotlin-2-4-20"),
)
compilerCompatibilityProjects.forEach { library ->
    library.pluginManager.withPlugin("maven-publish") {
        library.extensions.configure<PublishingExtension> {
            repositories.maven {
                name = "CompilerCompatibility"
                url = compilerCompatibilityRepository.get().asFile.toURI()
            }
        }
    }
}
tasks.register<Test>("compilerCompatibilityTest") {
    group = "verification"
    description = "Compiles JVM and mingwX64 consumers with each supported Kotlin compiler"
    val tests = sourceSets.named("test")
    testClassesDirs = tests.get().output.classesDirs
    classpath = tests.get().runtimeClasspath
    include("**/KotlinCompilerCompatibilityTest.class")
    compilerCompatibilityProjects.forEach { module ->
        dependsOn("${module.path.takeUnless { it == ":" }.orEmpty()}:publishAllPublicationsToCompilerCompatibilityRepository")
    }
    doFirst {
        systemProperty("winrt.test.repository", compilerCompatibilityRepository.get().asFile.toURI().toString())
        systemProperty("winrt.test.publicationVersion", project.version.toString())
    }
}

tasks.named<Test>("test") { exclude("**/KotlinCompilerCompatibilityTest.class") }
tasks.named("check") { dependsOn("compilerCompatibilityTest") }
tasks.named<Jar>("jar") { manifest.attributes("Implementation-Version" to project.version.toString()) }

gradlePlugin {
    plugins {
        create("kotlinWindowsToolkit") {
            id = "io.github.compose-fluent.windows-toolkit"
            implementationClass = "io.github.composefluent.windows.toolkit.gradle.KotlinWindowsToolkitPlugin"
        }
    }
}
