package io.github.composefluent.windows.toolkit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class JvmReferencePublicationTest(private val kotlinVersion: String) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "Kotlin {0}")
        fun versions(): List<Array<String>> = projectionTestCompilerVersions().map { arrayOf(it) }
    }
    @get:Rule val temporary = TemporaryFolder()

    @Test fun published_diamond_uses_complete_reference_for_compile_and_single_implementation_for_runtime() {
        val root = temporary.newFolder()
        fun write(path: String, text: String) = root.resolve(path).apply {
            parentFile.mkdirs(); writeText(text.trimIndent())
        }
        write("producer/settings.gradle", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { mavenCentral() } }
            rootProject.name = 'publisher'
            include 'sdk', 'a', 'b'
        """)
        listOf("producer", "consumer").forEach { directory -> write("$directory/gradle.properties", """
            org.gradle.jvmargs=-Xmx512m -XX:+UseSerialGC
            org.gradle.workers.max=1
            kotlin.compiler.execution.strategy=in-process
        """) }
        write("producer/build.gradle", """
            plugins {
                id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion' apply false
                id 'io.github.compose-fluent.windows-toolkit' apply false
            }
            subprojects {
                apply plugin: 'org.jetbrains.kotlin.jvm'
                apply plugin: 'java-library'
                apply plugin: 'maven-publish'
                group = 'sample'
                version = '1.0'
                publishing {
                    repositories { maven { name = 'fixture'; url = rootProject.file('../repository') } }
                    publications { library(MavenPublication) { from components.java } }
                }
            }
            project(':sdk') {
                afterEvaluate {
                    def compilation = tasks.named('compileKotlin').get()
                    def reference = io.github.composefluent.windows.toolkit.gradle.JvmProjectionReferenceArtifactKt
                        .configureJvmIncrementalReferenceArtifact(project, compilation)
                    io.github.composefluent.windows.toolkit.gradle.JvmProjectionReferencePublicationKt
                        .configureJvmProjectionReferencePublication(project, compilation, reference, files(), ['jar'])
                }
            }
            configure([project(':a'), project(':b')]) {
                dependencies { api project(':sdk') }
            }
        """)
        write("producer/sdk/src/main/kotlin/Projection.kt", """
            package sdk
            class Widget(val value: Int = 42)
            object WindowNative { fun windowHandle(widget: Widget) = widget.value }
            inline fun <reified T> typeName() = T::class.java.simpleName
        """)
        write("producer/sdk/src/main/java/sdk/JavaOverlay.java", """
            package sdk;
            public class JavaOverlay { public static int value() { return 7; } }
        """)
        write("producer/a/src/main/kotlin/A.kt", "package a; fun widget() = sdk.Widget()")
        write("producer/b/src/main/kotlin/B.kt", "package b; fun handle(value: sdk.Widget) = sdk.WindowNative.windowHandle(value)")
        GradleRunner.create().withProjectDir(root.resolve("producer")).withProjectionTestEnvironment()
            .withArguments("publishAllPublicationsToFixtureRepository", "--stacktrace").build()
        val repository = root.resolve("repository").toURI()
        java.util.zip.ZipFile(root.resolve("repository/sample/sdk/1.0/sdk-1.0-winrt-reference.jar")).use { archive ->
            val descriptor = archive.getEntry("META-INF/kotlin-winrt/binary-contract.json")
            assertNotNull(descriptor)
            val contents = archive.getInputStream(descriptor).bufferedReader().use { it.readText() }
            assertTrue(contents.contains("\"kotlinCompilerVersion\": \"$kotlinVersion\""))
            assertTrue(contents.contains("\"fingerprint\""))
        }
        write("consumer/settings.gradle", """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { maven { url = uri('$repository') }; mavenCentral() } }
            rootProject.name = 'external-consumer'
        """)
        write("consumer/build.gradle", """
            plugins { id 'org.jetbrains.kotlin.jvm' version '$kotlinVersion' }
            dependencies { implementation 'sample:a:1.0'; implementation 'sample:b:1.0' }
            tasks.register('verifyRun', JavaExec) {
                dependsOn classes
                classpath = sourceSets.main.runtimeClasspath
                mainClass = 'ConsumerKt'
                doFirst {
                    def contracts = sourceSets.main.compileClasspath.files.findAll { it.name.startsWith('sdk-') }
                    assert contracts*.name == ['sdk-1.0-winrt-reference.jar']
                    def implementations = classpath.files.findAll { it.name.startsWith('sdk-') }
                    assert implementations*.name == ['sdk-1.0.jar']
                }
            }
        """)
        write("consumer/src/main/kotlin/Consumer.kt", """
            fun main() {
                check(b.handle(a.widget()) == 42)
                check(sdk.WindowNative.windowHandle(sdk.Widget()) == 42)
                check(sdk.JavaOverlay.value() == 7)
                check(sdk.typeName<sdk.Widget>() == "Widget")
                println("PUBLISHED_DIAMOND_OK")
            }
        """)
        // Inject only the cached build-tool classpath; all tested libraries still resolve
        // exclusively from the published repository in this separate consumer build.
        val consumer = GradleRunner.create().withProjectDir(root.resolve("consumer")).withProjectionTestEnvironment()
            .withArguments("verifyRun", "--offline", "--stacktrace").build()
        assertTrue(consumer.output, consumer.output.contains("PUBLISHED_DIAMOND_OK"))
        val settings = root.resolve("consumer/settings.gradle")
        settings.writeText(settings.readText().replace("maven { url = uri('$repository') }", """
            maven { url = uri('$repository'); metadataSources {
                mavenPom(); artifact(); ignoreGradleMetadataRedirection()
            } }
        """.trimIndent()))
        val build = root.resolve("consumer/build.gradle")
        build.writeText(build.readText().replace("['sdk-1.0-winrt-reference.jar']", "['sdk-1.0.jar']"))
        val legacy = GradleRunner.create().withProjectDir(root.resolve("consumer")).withProjectionTestEnvironment()
            .withArguments("verifyRun", "--offline", "--stacktrace").build()
        assertTrue(legacy.output, legacy.output.contains("PUBLISHED_DIAMOND_OK"))
    }
}
