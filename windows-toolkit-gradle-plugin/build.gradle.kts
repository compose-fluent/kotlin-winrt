import org.gradle.api.tasks.testing.Test
import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import java.util.Properties

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    `java-gradle-plugin`
    alias(libs.plugins.gradlePluginPublish)
    id("build-convention")
    id("winrt.publish")
}

apply(plugin = "org.jetbrains.kotlin.jvm")

description = "Kotlin Windows toolkit Gradle plugin for WinRT projections, NuGet references, and Windows application packaging"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

extensions.configure<KotlinJvmProjectExtension> {
    compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
}

// Like CsWinRT's build support package, the plugin carries its tools without
// requiring their runtime when merely resolving the build integration. The
// Java 21 entry point checks for Java 25 before loading any WinRT tool classes.
val embeddedTools by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(KotlinPlatformType.attribute, KotlinPlatformType.jvm)
        attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25)
    }
}
val toolProjects = listOf(
    projects.ideModel,
    projects.winrtAuthoring, projects.winrtRuntime, projects.winrtMetadata, projects.winrtGenerator,
    projects.winrtCompilerPlugin, projects.winrtCompilerPlugin.callsiteContract,
    projects.winrtCompilerPlugin.callsiteLowering,
)
listOf("compileClasspath", "testCompileClasspath", "testRuntimeClasspath").forEach { name ->
    configurations.named(name) { attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 25) }
}

dependencies {
    compileOnly(gradleApi())
    toolProjects.forEach { tool ->
        compileOnly(tool)
        embeddedTools(tool)
        testImplementation(tool)
    }
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.io.core)
    // Supply KGP for toolkit-only builds, but let a consumer's explicit version win,
    // including older unsupported versions which must receive our own diagnostic.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin") {
        version { prefer(libs.versions.kotlin.get()) }
    }
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:${libs.versions.kotlin.get()}")
}

tasks.named<Jar>("jar") {
    from(embeddedTools.elements.map { files -> files.map { zipTree(it.asFile) } }) {
        exclude("META-INF/MANIFEST.MF", "META-INF/services/org.jetbrains.kotlin.compiler.plugin.*")
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
}

tasks.withType<Test>().configureEach {
    maxParallelForks = 1
    minHeapSize = "64m"
    maxHeapSize = "128m"
    jvmArgs("-XX:+UseSerialGC")
}

// TestKit may instrument/copy the plugin JAR into its cache. An explicit test-only
// resource locates the producer's artifact inventory without mistaking published
// consumers beneath build/ for TestKit consumers.
val testMetadataLocator = tasks.register("generateTestMetadataLocator") {
    val metadataFile = layout.buildDirectory.file("pluginUnderTestMetadata/plugin-under-test-metadata.properties")
    val output = layout.buildDirectory.dir("test-metadata-locator")
    inputs.property("metadataFile", metadataFile.map { it.asFile.absolutePath })
    outputs.dir(output)
    doLast {
        output.get().file("kotlin-winrt-test-metadata.properties").asFile.apply {
            parentFile.mkdirs()
            outputStream().use { stream ->
                Properties().apply { setProperty("metadata-file", metadataFile.get().asFile.absolutePath) }
                    .store(stream, null)
            }
        }
    }
}
tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.setFrom(
        tasks.named("jar"),
        embeddedTools,
        testMetadataLocator,
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
    website.set("https://github.com/compose-fluent/kotlin-winrt")
    vcsUrl.set("https://github.com/compose-fluent/kotlin-winrt")
    plugins {
        create("kotlinWindowsToolkit") {
            id = "io.github.compose-fluent.windows-toolkit"
            implementationClass = "io.github.composefluent.windows.toolkit.gradle.KotlinWindowsToolkitBootstrapPlugin"
            displayName = "Kotlin Windows Toolkit"
            description = project.description
            tags.set(listOf("kotlin", "windows", "winrt", "winui", "nuget"))
            compatibility {
                features {
                    configurationCache = true
                    isolatedProjects = false
                }
            }
        }
    }
}
