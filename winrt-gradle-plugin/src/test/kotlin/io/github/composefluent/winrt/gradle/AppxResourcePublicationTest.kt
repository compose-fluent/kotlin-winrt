package io.github.composefluent.winrt.gradle

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppxResourcePublicationTest {
    @Test
    fun maven_resource_variant_carries_transitive_appx_resources() {
        val root = Files.createTempDirectory("kotlin-winrt-appx-publication-")
        val repository = root.resolve("repository")
        val base = root.resolve("base")
        val library = root.resolve("library")
        val consumer = root.resolve("consumer")
        Files.createDirectories(repository)

        writeProducer(base, "base", null, repository, "Base.txt", "base")
        publish(base, repository)

        writeProducer(library, "library", "test.winrt:base:1.0", repository, "Library.txt", "library")
        publish(library, repository)

        writeSettings(consumer, repository, "consumer")
        writeGradleFile(
            consumer.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'io.github.compose-fluent.winrt'
            }

            dependencies {
                implementation 'test.winrt:library:1.0'
            }

            def appxResources = configurations.getByName('kotlinWinRTAppxResources')
            tasks.register('inspectAppxResources') {
                doLast {
                    def output = file("${'$'}buildDir/appx-resources.txt")
                    output.parentFile.mkdirs()
                    output.text = appxResources.files.collect { it.absolutePath }.sort().join(System.lineSeparator())
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(consumer.toFile())
            .withPluginClasspath()
            .withEnvironment(
                mapOf("GRADLE_USER_HOME" to root.resolve("gradle-user-home-consumer").toString()),
            )
            .withArguments("inspectAppxResources", "--stacktrace")
            .forwardOutput()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":inspectAppxResources")?.outcome)
        val archives = Files.readAllLines(consumer.resolve("build/appx-resources.txt"))
            .filter(String::isNotBlank)
            .map(Path::of)
        assertEquals(2, archives.size)
        val entries = archives.associate { archive ->
            ZipFile(archive.toFile()).use { zip ->
                val names = zip.entries().asSequence().map { it.name }.toSet()
                archive.fileName.toString() to names
            }
        }
        assertTrue(entries.values.any { "Assets/Base.txt" in it })
        assertTrue(entries.values.any { "Assets/Library.txt" in it })
        assertTrue(entries.values.none { names -> names.any { it.startsWith("appxResources/") } })
    }

    private fun publish(projectDir: Path, repository: Path) {
        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withPluginClasspath()
            .withEnvironment(
                mapOf("GRADLE_USER_HOME" to repository.resolve("gradle-user-home-${projectDir.fileName}").toString()),
            )
            .withArguments("publishMavenPublicationToTestRepository", "--stacktrace")
            .forwardOutput()
            .build()
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishMavenPublicationToTestRepository")?.outcome)
        assertTrue(Files.exists(repository.resolve("test/winrt/${projectDir.fileName}/1.0")))
    }

    private fun writeProducer(
        projectDir: Path,
        projectName: String,
        dependency: String?,
        repository: Path,
        resourceName: String,
        resourceText: String,
    ) {
        writeSettings(projectDir, repository, projectName)
        val resource = projectDir.resolve("src/main/appxResources/Assets/$resourceName")
        Files.createDirectories(resource.parent)
        Files.writeString(resource, resourceText)
        val dependencyBlock = dependency?.let { "implementation '$it'" }.orEmpty()
        writeGradleFile(
            projectDir.resolve("build.gradle"),
            """
            plugins {
                id 'java-library'
                id 'maven-publish'
                id 'io.github.compose-fluent.winrt'
            }

            group = 'test.winrt'
            version = '1.0'

            dependencies {
                $dependencyBlock
            }

            publishing {
                repositories {
                    maven {
                        name = 'test'
                        url = uri('${repository.toUri()}')
                    }
                }
                publications {
                    maven(MavenPublication) {
                        from components.java
                    }
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeSettings(projectDir: Path, repository: Path, name: String) {
        writeGradleFile(
            projectDir.resolve("settings.gradle"),
            """
            pluginManagement {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
            }
            dependencyResolutionManagement {
                repositories {
                    maven { url = uri('${repository.toUri()}') }
                    mavenCentral()
                }
            }
            rootProject.name = '$name'
            """.trimIndent(),
        )
        writeGradleFile(
            projectDir.resolve("gradle.properties"),
            """
            org.gradle.daemon=false
            org.gradle.workers.max=1
            org.gradle.jvmargs=-Xmx384m -XX:CICompilerCount=1 -XX:TieredStopAtLevel=1 -Dfile.encoding=UTF-8
            """.trimIndent(),
        )
    }

    private fun writeGradleFile(path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content + System.lineSeparator())
    }
}
